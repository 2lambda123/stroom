package stroom.statistics.impl.hbase.internal;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

public class KafkaTopicsConfig extends AbstractConfig {

    private String count = "statisticEvents-Count";
    private String value = "statisticEvents-Value";

    @JsonPropertyDescription("The kafka topic to send Count type stroom-stats statistic events to")
    public String getCount() {
        return count;
    }

    public void setCount(final String count) {
        this.count = count;
    }

    @JsonPropertyDescription("The kafka topic to send Value type stroom-stats statistic events to")
    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "KafkaTopicsConfig{" +
                "count='" + count + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
