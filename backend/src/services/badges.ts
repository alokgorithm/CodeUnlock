// ============================================================
// Badge Progress Computation Service
// ============================================================
import { SupabaseClient } from '@supabase/supabase-js';

/**
 * Update badge progress for a user after a solve event.
 */
export async function updateBadgeProgress(
  supabase: SupabaseClient,
  userId: string
): Promise<void> {
  // Get all badge definitions
  const { data: badges } = await supabase.from('badges').select('*');
  if (!badges) return;

  // Get user stats
  const { count: totalSolves } = await supabase
    .from('user_solves')
    .select('*', { count: 'exact', head: true })
    .eq('user_id', userId)
    .eq('verified', true);

  for (const badge of badges) {
    const req = badge.requirement as Record<string, number>;
    let progress = 0;

    if (req.solves) {
      progress = Math.min((totalSolves || 0) / req.solves, 1.0);
    } else if (req.streak_days) {
      const { data: dp } = await supabase
        .from('daily_progress')
        .select('date, problems_solved')
        .eq('user_id', userId)
        .order('date', { ascending: false })
        .limit(req.streak_days);
      
      let streak = 0;
      if (dp) {
        const today = new Date();
        for (let i = 0; i < dp.length; i++) {
          const expected = new Date(today);
          expected.setDate(expected.getDate() - i);
          if (dp[i]?.date === expected.toISOString().split('T')[0] && dp[i].problems_solved > 0) {
            streak++;
          } else break;
        }
      }
      progress = Math.min(streak / req.streak_days, 1.0);
    }

    const earnedAt = progress >= 1.0 ? new Date().toISOString() : null;

    await supabase
      .from('user_badges')
      .upsert({
        user_id: userId,
        badge_id: badge.id,
        badge_type: badge.category,
        badge_name: badge.name,
        progress,
        ...(earnedAt && { earned_at: earnedAt }),
      }, { onConflict: 'user_id,badge_id' });
  }
}
