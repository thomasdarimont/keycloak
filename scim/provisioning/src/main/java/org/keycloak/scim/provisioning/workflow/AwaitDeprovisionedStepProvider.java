package org.keycloak.scim.provisioning.workflow;

import java.util.ArrayList;
import java.util.List;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.workflow.WorkflowExecutionContext;
import org.keycloak.models.workflow.WorkflowStepProvider;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.model.OutboxEntryModel;
import org.keycloak.scim.provisioning.model.ScimLinkModel;
import org.keycloak.scim.provisioning.model.ScimProvisioningProvider;
import org.keycloak.scim.provisioning.planner.Subjects;

import org.jboss.logging.Logger;

/** EXPERIMENTAL. See {@link AwaitDeprovisionedStepProviderFactory}. */
public class AwaitDeprovisionedStepProvider implements WorkflowStepProvider {
    private static final Logger log = Logger.getLogger(AwaitDeprovisionedStepProvider.class);


    private final KeycloakSession session;
    private final ComponentModel model;

    AwaitDeprovisionedStepProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    /**
     * Done for a destination when nothing is pending for the user and the user has no live account
     * there (never had one, or it is tombstoned). A dead-lettered entry holds the gate unless {@code
     * onDeadLetter=continue}.
     */
    @Override
    public void run(WorkflowExecutionContext context) {
        RealmModel realm = session.getContext().getRealm();
        String userId = context.getResourceId();
        if (realm == null || userId == null) return;
        boolean continueOnDead =
                "continue"
                        .equals(
                                model == null
                                        ? null
                                        : model.get(AwaitDeprovisionedStepProviderFactory.ON_DEAD_LETTER));
        String subject = Subjects.user(userId);
        ScimProvisioningProvider outbox = session.getProvider(ScimProvisioningProvider.class);
        List<String> waiting = new ArrayList<>();
        for (Destination d :
                StepTargets.resolve(realm, model, AwaitDeprovisionedStepProviderFactory.ID)) {
            OutboxEntryModel e = outbox.getEntry(realm.getId(), d.id(), subject).orElse(null);
            if (e != null && e.isDead()) {
                if (!continueOnDead) waiting.add(d.name() + " (dead-lettered)");
                continue;
            }
            if (e != null && e.isPending()) {
                waiting.add(d.name());
                continue;
            }
            ScimLinkModel link = outbox.getLink(realm.getId(), d.id(), subject).orElse(null);
            if (link != null && link.isLive()) waiting.add(d.name() + " (account still active)");
        }
        if (!waiting.isEmpty()) {
            log.debugf("scim-provisioning: %s still waiting for %s", subject, waiting);
            // failing is how a step asks the engine to run it again later
            throw new IllegalStateException(
                    "scim-provisioning: " + subject + " not yet deprovisioned in " + waiting);
        }
    }

    @Override
    public void close() {}
}
