package org.femass.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

@ApplicationScoped
public class CampaignClock {
    @Produces
    @ApplicationScoped
    public Clock clock() { return Clock.systemUTC(); }
}
