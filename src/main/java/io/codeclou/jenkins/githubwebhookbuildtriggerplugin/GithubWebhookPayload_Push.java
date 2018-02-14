/*
 * Licensed under MIT License
 * Copyright (c) 2017 Bernhard Grünewaldt
 */
package io.codeclou.jenkins.githubwebhookbuildtriggerplugin;

/**
 * GitHub Webhook JSON Pojo with only the parts that are interesting for us.
 * See: https://developer.github.com/webhooks/#payloads
 */
public class GithubWebhookPayload_Push {

    /*
     * hook_id is only set on initial request when the webhook is created.
     * See: https://developer.github.com/webhooks/#ping-event
     */
    private Long hook_id;
    private String ref;
    private String before;
    private String after;
    private GithubWebhookPayloadRepository repository;

    public GithubWebhookPayload_Push() {

    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getBefore() {
        return before;
    }

    public void setBefore(String before) {
        this.before = before;
    }

    public String getAfter() {
        return after;
    }

    public void setAfter(String after) {
        this.after = after;
    }

    public GithubWebhookPayloadRepository getRepository() {
        return repository;
    }

    public void setRepository(GithubWebhookPayloadRepository repository) {
        this.repository = repository;
    }

    public Long getHook_id() {
        return hook_id;
    }

    public void setHook_id(Long hook_id) {
        this.hook_id = hook_id;
    }
}
