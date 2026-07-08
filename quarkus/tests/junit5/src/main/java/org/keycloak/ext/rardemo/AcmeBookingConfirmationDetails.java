package org.keycloak.ext.rardemo;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.keycloak.representations.AuthorizationDetailsJSONRepresentation;

public class AcmeBookingConfirmationDetails extends AuthorizationDetailsJSONRepresentation {

    @JsonProperty("name")
    String name;

    @JsonProperty("cost")
    String cost;

    @JsonProperty("confirmation_number")
    String confirmationNumber;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCost() {
        return cost;
    }

    public void setCost(String cost) {
        this.cost = cost;
    }

    public String getConfirmationNumber() {
        return confirmationNumber;
    }

    public void setConfirmationNumber(String confirmationNumber) {
        this.confirmationNumber = confirmationNumber;
    }
}
