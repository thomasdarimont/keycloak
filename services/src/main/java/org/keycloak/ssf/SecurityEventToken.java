package org.keycloak.ssf;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.keycloak.json.StringOrArrayDeserializer;
import org.keycloak.json.StringOrArraySerializer;

import java.io.Serializable;
import java.util.Arrays;

public class SecurityEventToken implements Serializable {

    protected Long iat;

    @JsonProperty("jti")
    protected String id;

    @JsonProperty("iss")
    protected String issuer;

    @JsonProperty("aud")
    @JsonSerialize(using = StringOrArraySerializer.class)
    @JsonDeserialize(using = StringOrArrayDeserializer.class)
    protected String[] audience;

    public SecurityEventToken audience(String... audience) {
        this.audience = audience;
        return this;
    }

    public SecurityEventToken addAudience(String audience) {
        if (this.audience == null) {
            this.audience = new String[]{audience};
        } else {
            // Check if audience is already there
            for (String aud : this.audience) {
                if (audience.equals(aud)) {
                    return this;
                }
            }

            String[] newAudience = Arrays.copyOf(this.audience, this.audience.length + 1);
            newAudience[this.audience.length] = audience;
            this.audience = newAudience;
        }
        return this;
    }

    public String[] getAudience() {
        return audience;
    }

    public Long getIat() {
        return iat;
    }

    public void setIat(Long iat) {
        this.iat = iat;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public void setAudience(String[] audience) {
        this.audience = audience;
    }
}
