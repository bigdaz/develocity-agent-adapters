package com.gradle.develocity.agent.gradle.adapters;

import org.gradle.api.Action;
import org.gradle.caching.configuration.AbstractBuildCache;

import javax.annotation.Nullable;

public interface DevelocityAdapter {

    BuildScanAdapter getBuildScan();

    void buildScan(Action<? super BuildScanAdapter> action);

    void setServer(@Nullable String server);

    @Nullable
    String getServer();

    void setProjectId(@Nullable String projectId);

    @Nullable
    String getProjectId();

    void setAllowUntrustedServer(boolean allow);

    boolean getAllowUntrustedServer();

    void setAccessKey(@Nullable String accessKey);

    @Nullable
    String getAccessKey();

    @Nullable
    Class<? extends AbstractBuildCache> getBuildCache();

}