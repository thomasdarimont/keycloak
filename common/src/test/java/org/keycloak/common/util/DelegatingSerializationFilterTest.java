package org.keycloak.common.util;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public class DelegatingSerializationFilterTest {

    @Test
    public void settingObjectInputFilterShouldNotThrowOnAlreadyUsedObjectInputStream() throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);

        oos.writeObject(new Data());
        oos.writeObject(new Data());


        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));

        // simulate prior ObjectInputStream use
        Object result = ois.readObject();

        DelegatingSerializationFilter.builder()
                .addAllowedPattern("*")
                .setFilter(ois);

        result = ois.readObject();
        Assert.assertNotNull(result);

    }

    public static class Data implements Serializable {
        String s = "foo";
    }
}