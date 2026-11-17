package io.quarkus.observability.test.utils;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ElasticSearchResult {
    public Hits hits;

    @Override
    public String toString() {
        return "ElasticSearchResult{" +
                "hits=" + hits +
                '}';
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hits {
        public Total total;
        public List<Hit> hits;

        @Override
        public String toString() {
            return "Hits{" +
                    "total=" + total +
                    ", hits=" + (hits != null ? hits.size() : 0) +
                    '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Total {
        public int value;
        public String relation;

        @Override
        public String toString() {
            return "Total{" +
                    "value=" + value +
                    ", relation='" + relation + '\'' +
                    '}';
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Hit {
        public String _index;
        public String _id;
        public Map<String, Object> _source;

        @Override
        public String toString() {
            return "Hit{" +
                    "_index='" + _index + '\'' +
                    ", _id='" + _id + '\'' +
                    '}';
        }
    }
}
