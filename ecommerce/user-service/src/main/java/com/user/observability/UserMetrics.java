package com.user.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class UserMetrics {

    private final Counter userCreated;
    private final Counter userUpdated;
    private final Counter userDeleted;
    public  final Timer   userFetchTimer;

    public UserMetrics(MeterRegistry registry) {
        this.userCreated = Counter.builder("user.created")
                .description("Total users registered")
                .register(registry);
        this.userUpdated = Counter.builder("user.updated")
                .description("Total user profile updates")
                .register(registry);
        this.userDeleted = Counter.builder("user.deleted")
                .description("Total users deleted")
                .register(registry);
        this.userFetchTimer = Timer.builder("user.fetch.duration")
                .description("Latency of user fetch by ID")
                .register(registry);
    }

    public void incrementCreated()  { userCreated.increment(); }
    public void incrementUpdated()  { userUpdated.increment(); }
    public void incrementDeleted()  { userDeleted.increment(); }
}
