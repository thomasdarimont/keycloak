package org.keycloak.models.analytics;

import java.util.List;

public class TimeSeries<DataPoint> {

    private String name;
    private List<DataPoint> dataPoints;

    public TimeSeries(String name, List<DataPoint> dataPoints) {
        this.name = name;
        this.dataPoints = dataPoints;
    }

    public List<DataPoint> getDataPoints() {
        return dataPoints;
    }

    public void setDataPoints(List<DataPoint> dataPoints) {
        this.dataPoints = dataPoints;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
