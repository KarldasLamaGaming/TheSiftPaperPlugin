package de.lamamc.sift.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PagingTest {

    @Test
    void pageCountHasAtLeastOnePage() {
        assertEquals(1, Paging.pageCount(0, 45));
        assertEquals(1, Paging.pageCount(45, 45));
        assertEquals(2, Paging.pageCount(46, 45));
    }

    @Test
    void indexMapsSlotsOfAPage() {
        assertEquals(0, Paging.index(0, 0, 50, 45));
        assertEquals(45, Paging.index(1, 0, 50, 45));
        assertEquals(49, Paging.index(1, 4, 50, 45));
        assertEquals(-1, Paging.index(1, 5, 50, 45));
        assertEquals(-1, Paging.index(0, 45, 50, 45));
        assertEquals(-1, Paging.index(0, 0, 0, 45));
    }

    @Test
    void pagesAreClamped() {
        assertEquals(0, Paging.clamp(-1, 50, 45));
        assertEquals(1, Paging.clamp(7, 50, 45));
        // a page number beyond the end shows the last page
        assertEquals(45, Paging.index(9, 0, 50, 45));
    }
}
