package com.sumo.config;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import com.sumo.dao.DeviceDao;
import com.sumo.dao.spanner.SpannerDeviceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for Google Cloud Spanner.
 * Provides database client and DAO bean configuration.
 */
public class SpannerConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(SpannerConfig.class);
    
    private final String projectId;
    private final String instanceId;
    private final String databaseId;
    
    public SpannerConfig(String projectId, String instanceId, String databaseId) {
        this.projectId = projectId;
        this.instanceId = instanceId;
        this.databaseId = databaseId;
    }
    
    /**
     * Create and configure Spanner client.
     * @return configured Spanner instance
     */
    public Spanner createSpannerClient() {
        logger.info("Creating Spanner client for project: {}, instance: {}, database: {}", 
                   projectId, instanceId, databaseId);
        
        SpannerOptions options = SpannerOptions.newBuilder()
            .setProjectId(projectId)
            .build();
        
        return options.getService();
    }
    
    /**
     * Create database client.
     * @return configured DatabaseClient
     */
    public DatabaseClient createDatabaseClient() {
        Spanner spanner = createSpannerClient();
        DatabaseId dbId = DatabaseId.of(projectId, instanceId, databaseId);
        return spanner.getDatabaseClient(dbId);
    }
    
    /**
     * Create DeviceDao bean.
     * @return configured DeviceDao
     */
    public DeviceDao deviceDao() {
        return new SpannerDeviceDao(createDatabaseClient());
    }

}
