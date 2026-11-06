
package com.example.cicddemo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HomeControllerTest {

    private final HomeController controller = new HomeController();

    @Test
    void homeEndpointTest() {
        assertEquals(
            "Welcome to My Automated CI/CD Project 2!",
            controller.home()
        );
    }

    @Test
    void healthEndpointTest() {
        assertEquals(
            "Application is running!",
            controller.health()
        );
    }
}
