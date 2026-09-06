package opensagetv.vibe.miniclient.video;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PlaybackSyncPointPolicyTest
{
    @Test
    public void explicitPoliciesDoNotDependOnDirection()
    {
        assertEquals(PlaybackSyncPointPolicy.Target.NEXT,
                PlaybackSyncPointPolicy.choose("next", 10000L, 1000L, 2000L));
        assertEquals(PlaybackSyncPointPolicy.Target.PREVIOUS,
                PlaybackSyncPointPolicy.choose("previous", 1000L, 10000L, 2000L));
        assertEquals(PlaybackSyncPointPolicy.Target.CLOSEST,
                PlaybackSyncPointPolicy.choose("closest", 1000L, 10000L, 2000L));
    }

    @Test
    public void directionalPolicyUsesThresholdAndDirection()
    {
        assertEquals(PlaybackSyncPointPolicy.Target.CLOSEST,
                PlaybackSyncPointPolicy.choose("directional", 10000L, 11999L, 2000L));
        assertEquals(PlaybackSyncPointPolicy.Target.NEXT,
                PlaybackSyncPointPolicy.choose("directional", 10000L, 12000L, 2000L));
        assertEquals(PlaybackSyncPointPolicy.Target.PREVIOUS,
                PlaybackSyncPointPolicy.choose("directional", 10000L, 8000L, 2000L));
    }

    @Test
    public void unknownAndNullPoliciesUseClosest()
    {
        assertEquals(PlaybackSyncPointPolicy.Target.CLOSEST,
                PlaybackSyncPointPolicy.choose(null, 0L, 1L, 0L));
        assertEquals(PlaybackSyncPointPolicy.Target.CLOSEST,
                PlaybackSyncPointPolicy.choose("unknown", 0L, 1L, 0L));
    }

    @Test
    public void wireNamesRemainStable()
    {
        assertEquals("closest_sync", PlaybackSyncPointPolicy.wireName(PlaybackSyncPointPolicy.Target.CLOSEST));
        assertEquals("next_sync", PlaybackSyncPointPolicy.wireName(PlaybackSyncPointPolicy.Target.NEXT));
        assertEquals("previous_sync", PlaybackSyncPointPolicy.wireName(PlaybackSyncPointPolicy.Target.PREVIOUS));
    }
}
