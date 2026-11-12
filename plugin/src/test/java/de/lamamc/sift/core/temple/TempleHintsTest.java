package de.lamamc.sift.core.temple;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TempleHintsTest {

    @Test
    void directionsFollowMinecraftAxes() {
        assertEquals("Norden", TempleHints.direction(0, 0, 0, -100));
        assertEquals("Süden", TempleHints.direction(0, 0, 0, 100));
        assertEquals("Osten", TempleHints.direction(0, 0, 100, 0));
        assertEquals("Westen", TempleHints.direction(0, 0, -100, 0));
        assertEquals("Nordosten", TempleHints.direction(0, 0, 100, -100));
        assertEquals("Südwesten", TempleHints.direction(10, 10, -90, 110));
        assertEquals("Nordwesten", TempleHints.direction(0, 0, -100, -90));
        // 22° east of north still counts as north, 23° as north-east
        assertEquals("Norden", TempleHints.direction(0, 0, Math.tan(Math.toRadians(22)) * 100, -100));
        assertEquals("Nordosten", TempleHints.direction(0, 0, Math.tan(Math.toRadians(23)) * 100, -100));
        assertEquals("hier", TempleHints.direction(5, 5, 5, 5));
    }

    @Test
    void distanceIsRounded() {
        assertEquals(1200, TempleHints.roundedDistance(0, 0, 1234, 0, 100));
        assertEquals(1300, TempleHints.roundedDistance(0, 0, 1250, 0, 100));
        assertEquals(100, TempleHints.roundedDistance(0, 0, 3, 4, 100), "never 0");
        assertEquals(5, TempleHints.roundedDistance(0, 0, 3, 4, 1));
    }

    @Test
    void nearestOpenSiteSkipsDefeatedAndExcluded() {
        List<TempleSites.Site> sites = List.of(new TempleSites.Site(0, 100, 0), new TempleSites.Site(1, 300, 0),
                new TempleSites.Site(2, -1000, 0));
        Set<String> defeated = Set.of("temple-1");
        var open = TempleSites.nearest(sites, 0, 0, s -> !defeated.contains(s.id()));
        assertEquals("temple-2", open.id());
        var notHere = TempleSites.nearest(sites, 300, 0, s -> !defeated.contains(s.id()) && !s.id().equals("temple-2"));
        assertEquals("temple-3", notHere.id());
        assertEquals(null, TempleSites.nearest(sites, 0, 0, s -> false));
    }

    @Test
    void directionKeysMatchTheWords() {
        org.junit.jupiter.api.Assertions.assertEquals("n", TempleHints.directionKey(0, 0, 0, -100));
        org.junit.jupiter.api.Assertions.assertEquals("se", TempleHints.directionKey(0, 0, 100, 100));
        org.junit.jupiter.api.Assertions.assertEquals("w", TempleHints.directionKey(0, 0, -100, 0));
        org.junit.jupiter.api.Assertions.assertEquals("here", TempleHints.directionKey(5, 5, 5, 5));
    }
}
