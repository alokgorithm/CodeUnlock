import { SupabaseClient } from '@supabase/supabase-js';
import { AuthUser } from '../types';
import { success } from '../utils/response';

export async function handleGetBadges(
  supabase: SupabaseClient,
  user: AuthUser
): Promise<Response> {
  const { data: badges } = await supabase
    .from('badges')
    .select('id, name, category, requirement, icon_emoji, description')
    .order('id', { ascending: true });

  const { data: userBadges } = await supabase
    .from('user_badges')
    .select('badge_id, progress, earned_at')
    .eq('user_id', user.id);

  const progressByBadge = new Map(
    (userBadges || []).map((entry) => [entry.badge_id, entry])
  );

  return success({
    badges: (badges || []).map((badge) => {
      const progress = progressByBadge.get(badge.id);
      return {
        badge_id: badge.id,
        name: badge.name,
        category: badge.category,
        icon_emoji: badge.icon_emoji,
        description: badge.description,
        progress: progress?.progress || 0,
        earned_at: progress?.earned_at || null,
      };
    }),
  });
}
