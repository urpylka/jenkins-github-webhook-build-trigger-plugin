/*
 * Licensed under MIT License
 * Copyright (c) 2017 Bernhard Grünewaldt
 */
package io.codeclou.jenkins.githubwebhookbuildtriggerplugin;

/**
 * GitHub Webhook JSON Pojo with only the parts that are interesting for us.
 * See: https://developer.github.com/webhooks/#payloads
 */
public class GithubWebhookPayload_Release {

    private GithubWebhookPayloadRelease release;
    private GithubWebhookPayloadRepository repository;

    public GithubWebhookPayload_Release() {

    }

    public GithubWebhookPayloadRelease getRelease() {
        return release;
    }

    public void setRelease(GithubWebhookPayloadRelease release) {
        this.release = release;
    }

    public GithubWebhookPayloadRepository getRepository() {
        return repository;
    }

    public void setRepository(GithubWebhookPayloadRepository repository) {
        this.repository = repository;
    }
}
