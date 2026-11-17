package io.quarkus.observability.test.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElasticClusterHealth {
    @JsonProperty("cluster_name")
    public String clusterName;
    public String status;
    @JsonProperty("number_of_nodes")
    public int numberOfNodes;

    @Override
    public String toString() {
        return "ElasticClusterHealth{" +
                "clusterName='" + clusterName + '\'' +
                ", status='" + status + '\'' +
                ", numberOfNodes=" + numberOfNodes +
                '}';
    }
}
