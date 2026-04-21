package com.mal.lospoc.los.infrastructure;

import com.mal.lospoc.common.domain.ApplicationEvent;
import com.mal.lospoc.common.domain.LoanApplication;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryApplicationStore {
    private final Map<UUID, LoanApplication> applications = new ConcurrentHashMap<>();
    private final Map<UUID, List<ApplicationEvent>> events = new ConcurrentHashMap<>();

    public void save(LoanApplication application) {
        applications.put(application.applicationId(), application);
    }

    public Optional<LoanApplication> findById(UUID applicationId) {
        return Optional.ofNullable(applications.get(applicationId));
    }

    public void appendEvent(UUID applicationId, ApplicationEvent event) {
        events.computeIfAbsent(applicationId, k -> new ArrayList<>()).add(event);
    }

    public List<ApplicationEvent> getEvents(UUID applicationId) {
        return events.getOrDefault(applicationId, List.of());
    }

    public Collection<LoanApplication> findAll() {
        return applications.values();
    }
}
