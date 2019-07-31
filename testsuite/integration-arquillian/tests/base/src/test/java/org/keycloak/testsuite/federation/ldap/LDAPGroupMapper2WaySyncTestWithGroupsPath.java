/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.testsuite.federation.ldap;

import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.LDAPConstants;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.ldap.LDAPStorageProvider;
import org.keycloak.storage.ldap.LDAPStorageProviderFactory;
import org.keycloak.storage.ldap.mappers.membership.LDAPGroupMapperMode;
import org.keycloak.storage.ldap.mappers.membership.group.GroupLDAPStorageMapperFactory;
import org.keycloak.storage.ldap.mappers.membership.group.GroupMapperConfig;
import org.keycloak.storage.user.SynchronizationResult;
import org.keycloak.testsuite.util.LDAPRule;
import org.keycloak.testsuite.util.LDAPTestUtils;

/**
 * @author <a href="mailto:mposolda@redhat.com">Marek Posolda</a>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LDAPGroupMapper2WaySyncTestWithGroupsPath extends AbstractLDAPTest {

    private static final String LDAP_GROUPS_PATH = "/Applications/App1";

    @ClassRule
    public static LDAPRule ldapRule = new LDAPRule();

    private static ComponentModel ldapModel = null;
    private static String descriptionAttrName = null;

    @Override
    protected LDAPRule getLDAPRule() {
        return ldapRule;
    }

    @Override
    protected void afterImportTestRealm() {
        testingClient.server().run(session -> {
            LDAPTestContext ctx = LDAPTestContext.init(session);
            RealmModel appRealm = ctx.getRealm();

            UserStorageProviderModel ldapModel = ctx.getLdapModel();
            ldapModel.put(LDAPConstants.SYNC_REGISTRATIONS, "true");
            ldapModel.put(LDAPConstants.EDIT_MODE, UserStorageProvider.EditMode.WRITABLE.toString());
            ldapModel.put(LDAPConstants.BATCH_SIZE_FOR_SYNC, "4"); // Issues with pagination on ApacheDS

            ldapModel.setLastSync(0);
            ldapModel.setChangedSyncPeriod(-1);
            ldapModel.setFullSyncPeriod(-1);
            ldapModel.setName("test-ldap");
            ldapModel.setPriority(0);
            ldapModel.setProviderId(LDAPStorageProviderFactory.PROVIDER_NAME);

            appRealm.updateComponent(ldapModel);

            LDAPStorageProvider ldapFedProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);
            descriptionAttrName = ldapFedProvider.getLdapIdentityStore().getConfig().isActiveDirectory() ? "displayName" : "description";

            // Create LDAP groups path group
            GroupModel parentGroup = appRealm.createGroup("Applications");
            appRealm.moveGroup(parentGroup, null);
            appRealm.moveGroup(appRealm.createGroup("App1"), parentGroup);

            // Add group mapper
            LDAPTestUtils.addOrUpdateGroupMapper(appRealm, ldapModel, LDAPGroupMapperMode.LDAP_ONLY, descriptionAttrName, GroupMapperConfig.LDAP_GROUPS_PATH, LDAP_GROUPS_PATH);

            // Remove all LDAP groups
            LDAPTestUtils.removeAllLDAPGroups(session, appRealm, ldapModel, "groupsMapper");

            // Add some groups for testing into Keycloak
            GroupModel groupsPath = KeycloakModelUtils.findGroupByPath(appRealm, LDAP_GROUPS_PATH);

            GroupModel group1 = appRealm.createGroup("group1");
            appRealm.moveGroup(group1, groupsPath);
            group1.setSingleAttribute(descriptionAttrName, "group1 - description1");

            GroupModel group11 = appRealm.createGroup("group11");
            appRealm.moveGroup(group11, group1);

            GroupModel group12 = appRealm.createGroup("group12");
            appRealm.moveGroup(group12, group1);
            group12.setSingleAttribute(descriptionAttrName, "group12 - description12");

            GroupModel group2 = appRealm.createGroup("group2");
            appRealm.moveGroup(group2, groupsPath);
        });
    }

    @Test
    public void test01_syncNoPreserveGroupInheritance() throws Exception {
        testingClient.server().run(session -> {
            RealmModel realm = session.realms().getRealmByName("test");
            ComponentModel mapperModel = LDAPTestUtils.getSubcomponentByName(realm, ldapModel, "groupsMapper");
            LDAPStorageProvider ldapProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);

            // Update group mapper to skip preserve inheritance and check it will pass now
            LDAPTestUtils.updateGroupMapperConfigOptions(mapperModel, GroupMapperConfig.PRESERVE_GROUP_INHERITANCE, "false");
            realm.updateComponent(mapperModel);

            // Sync from Keycloak into LDAP
            SynchronizationResult syncResult = new GroupLDAPStorageMapperFactory().create(session, mapperModel).syncDataFromKeycloakToFederationProvider(realm);
            LDAPTestAsserts.assertSyncEquals(syncResult, 4, 0, 0, 0);
        });

        testingClient.server().run(session -> {
            RealmModel realm = session.realms().getRealmByName("test");

            // Delete all KC groups now
            removeAllModelGroups(realm);
            Assert.assertNull(KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group1"));
            Assert.assertNull(KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group11"));
            Assert.assertNull(KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group2"));
        });


        testingClient.server().run(session -> {
            RealmModel realm = session.realms().getRealmByName("test");
            ComponentModel mapperModel = LDAPTestUtils.getSubcomponentByName(realm, ldapModel, "groupsMapper");
            LDAPStorageProvider ldapProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);

            // Sync from LDAP back into Keycloak
            SynchronizationResult syncResult = new GroupLDAPStorageMapperFactory().create(session, mapperModel).syncDataFromFederationProviderToKeycloak(realm);
            LDAPTestAsserts.assertSyncEquals(syncResult, 4, 0, 0, 0);

            // Assert groups are imported to keycloak. All are at groups path
            GroupModel kcGroup1 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group1");
            GroupModel kcGroup11 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group11");
            GroupModel kcGroup12 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group12");
            GroupModel kcGroup2 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group2");

            Assert.assertEquals(0, kcGroup1.getSubGroups().size());

            Assert.assertEquals("group1 - description1", kcGroup1.getFirstAttribute(descriptionAttrName));
            Assert.assertNull(kcGroup11.getFirstAttribute(descriptionAttrName));
            Assert.assertEquals("group12 - description12", kcGroup12.getFirstAttribute(descriptionAttrName));
            Assert.assertNull(kcGroup2.getFirstAttribute(descriptionAttrName));

            // test drop non-existing works
            testDropNonExisting(session, realm, mapperModel, ldapProvider);
        });
    }

    @Test
    public void test02_syncWithGroupInheritance() throws Exception {

        testingClient.server().run(session -> {
            RealmModel realm = session.realms().getRealmByName("test");
            ComponentModel mapperModel = LDAPTestUtils.getSubcomponentByName(realm, ldapModel, "groupsMapper");
            LDAPStorageProvider ldapProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);

            // Update group mapper to skip preserve inheritance and check it will pass now
            LDAPTestUtils.updateGroupMapperConfigOptions(mapperModel, GroupMapperConfig.PRESERVE_GROUP_INHERITANCE, "true");
            realm.updateComponent(mapperModel);

            // Sync from Keycloak into LDAP
            SynchronizationResult syncResult = new GroupLDAPStorageMapperFactory().create(session, mapperModel).syncDataFromKeycloakToFederationProvider(realm);
            LDAPTestAsserts.assertSyncEquals(syncResult, 4, 0, 0, 0);
        });

        testingClient.server().run(session -> {
            RealmModel realm = session.realms().getRealmByName("test");

            // Delete all KC groups now
            removeAllModelGroups(realm);
            Assert.assertNull(KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group1"));
            Assert.assertNull(KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group11"));
            Assert.assertNull(KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group2"));
        });


        testingClient.server().run(session -> {
            RealmModel realm = session.realms().getRealmByName("test");
            ComponentModel mapperModel = LDAPTestUtils.getSubcomponentByName(realm, ldapModel, "groupsMapper");
            LDAPStorageProvider ldapProvider = LDAPTestUtils.getLdapProvider(session, ldapModel);

            // Sync from LDAP back into Keycloak
            SynchronizationResult syncResult = new GroupLDAPStorageMapperFactory().create(session, mapperModel).syncDataFromFederationProviderToKeycloak(realm);
            LDAPTestAsserts.assertSyncEquals(syncResult, 4, 0, 0, 0);

            // Assert groups are imported to keycloak. All are at top level
            GroupModel kcGroup1 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group1");
            GroupModel kcGroup11 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group1/group11");
            GroupModel kcGroup12 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group1/group12");
            GroupModel kcGroup2 = KeycloakModelUtils.findGroupByPath(realm, LDAP_GROUPS_PATH + "/group2");

            Assert.assertEquals(2, kcGroup1.getSubGroups().size());

            Assert.assertEquals("group1 - description1", kcGroup1.getFirstAttribute(descriptionAttrName));
            Assert.assertNull(kcGroup11.getFirstAttribute(descriptionAttrName));
            Assert.assertEquals("group12 - description12", kcGroup12.getFirstAttribute(descriptionAttrName));
            Assert.assertNull(kcGroup2.getFirstAttribute(descriptionAttrName));

            // test drop non-existing works
            testDropNonExisting(session, realm, mapperModel, ldapProvider);
        });
    }


    private static void removeAllModelGroups(RealmModel appRealm) {
        for (GroupModel group : KeycloakModelUtils.findGroupByPath(appRealm, LDAP_GROUPS_PATH).getSubGroups()) {
            appRealm.removeGroup(group);
        }
    }

    private void testDropNonExisting(KeycloakSession session, RealmModel realm, ComponentModel mapperModel, LDAPStorageProvider ldapProvider) {
        // Put some group directly to LDAP
        LDAPTestUtils.createLDAPGroup(session, realm, ldapModel, "group3");

        // Sync and assert our group is still in LDAP
        SynchronizationResult syncResult = new GroupLDAPStorageMapperFactory().create(session, mapperModel).syncDataFromKeycloakToFederationProvider(realm);
        LDAPTestAsserts.assertSyncEquals(syncResult, 0, 4, 0, 0);
        Assert.assertNotNull(LDAPTestUtils.getGroupMapper(mapperModel, ldapProvider, realm).loadLDAPGroupByName("group3"));

        // Change config to drop non-existing groups
        LDAPTestUtils.updateGroupMapperConfigOptions(mapperModel, GroupMapperConfig.DROP_NON_EXISTING_GROUPS_DURING_SYNC, "true");
        realm.updateComponent(mapperModel);

        // Sync and assert group removed from LDAP
        syncResult = new GroupLDAPStorageMapperFactory().create(session, mapperModel).syncDataFromKeycloakToFederationProvider(realm);
        LDAPTestAsserts.assertSyncEquals(syncResult, 0, 4, 1, 0);
        Assert.assertNull(LDAPTestUtils.getGroupMapper(mapperModel, ldapProvider, realm).loadLDAPGroupByName("group3"));
    }
}
