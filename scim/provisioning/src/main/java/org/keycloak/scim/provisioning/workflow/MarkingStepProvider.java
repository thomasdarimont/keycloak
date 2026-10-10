package org.keycloak.scim.provisioning.workflow;

import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.workflow.WorkflowExecutionContext;
import org.keycloak.models.workflow.WorkflowStepProvider;
import org.keycloak.scim.provisioning.component.Destination;
import org.keycloak.scim.provisioning.listener.Enqueuer;
import org.keycloak.scim.provisioning.planner.Subjects;

import org.jboss.logging.Logger;

/**
 * EXPERIMENTAL. A step that only writes markers for the user in the targeted destinations — quick
 * (the engine's step timeout is 5 s) and idempotent under the engine's unbounded retries. The
 * drainer does the delivery.
 */
class MarkingStepProvider implements WorkflowStepProvider {
    private static final Logger log = Logger.getLogger(MarkingStepProvider.class);


    private final KeycloakSession session;
    private final ComponentModel model;
    private final String stepId;
    private final boolean forced;
    private final String forcedMode;

    MarkingStepProvider(
            KeycloakSession session,
            ComponentModel model,
            String stepId,
            boolean forced,
            String forcedMode) {
        this.session = session;
        this.model = model;
        this.stepId = stepId;
        this.forced = forced;
        this.forcedMode = forcedMode;
    }

    @Override
    public void run(WorkflowExecutionContext context) {
        RealmModel realm = session.getContext().getRealm();
        String userId = context.getResourceId();
        if (realm == null || userId == null) {
            log.warnf(
                    "scim-provisioning: workflow step %s ran without a realm or user; nothing marked", stepId);
            return;
        }
        String subject = Subjects.user(userId);
        for (Destination d : StepTargets.resolve(realm, model, stepId)) {
            Enqueuer.now(session, new Enqueuer.Mark(realm.getId(), d.id(), subject, forced, forcedMode));
        }
        log.debugf("scim-provisioning: workflow step %s marked %s", stepId, subject);
    }

    @Override
    public void close() {}
}
