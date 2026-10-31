package com.spendwise.util;

import java.awt.Image;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppIconTest {

    @Test
    void loadsApplicationIconAtMultipleResolutions() {
        List<Image> images = AppIcon.images();

        assertEquals(7, images.size());
        assertEquals(List.of(16, 24, 32, 48, 64, 128, 256),
                images.stream().map(image -> image.getWidth(null)).toList());
        assertEquals(List.of(16, 24, 32, 48, 64, 128, 256),
                images.stream().map(image -> image.getHeight(null)).toList());
    }
}
