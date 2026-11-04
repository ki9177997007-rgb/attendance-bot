package ru.videofactory.publisher;

import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory state model. Persistent queue and UI adapters are not implemented yet. */
public final class PublicationQueue {
    public enum Status { WAITING, READY, UPLOADING, PUBLISHED, FAILED, NEEDS_REVIEW }
    private final Map<String, Status> jobs = new LinkedHashMap<>();
    public PublicationQueue(String videoId) {
        for (String target : new String[]{"vk", "youtube", "tiktok", "dzen"}) {
            jobs.put(videoId + ":" + target, Status.WAITING);
        }
    }
    public synchronized Map<String, Status> snapshot() {
        return new LinkedHashMap<>(jobs);
    }
    public synchronized void mark(String jobId, Status status) {
        if (!jobs.containsKey(jobId)) throw new IllegalArgumentException("Unknown job");
        jobs.put(jobId, status);
    }
}
