import ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import {
  Action,
  KeycloakDataTable,
  ListEmptyState,
  useAlerts,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import { Button, ButtonVariant, ToolbarItem } from "@patternfly/react-core";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router-dom";
import { useAdminClient } from "../../admin-client";
import { ComponentTypeDialog } from "../../components/component-type-dialog/ComponentTypeDialog";
import { useConfirmDialog } from "../../components/confirm-dialog/ConfirmDialog";
import { useRealm } from "../../context/realm-context/RealmContext";
import useToggle from "../../utils/useToggle";
import {
  toClientAccessPolicy,
  toNewClientAccessPolicy,
} from "../routes/ClientAccessPolicy";
import { CLIENT_ACCESS_POLICY_TYPE } from "./constants";

const DetailLink = (policy: ComponentRepresentation) => {
  const { realm } = useRealm();
  return (
    <Link key={policy.id} to={toClientAccessPolicy({ realm, id: policy.id! })}>
      {policy.name}
    </Link>
  );
};

/**
 * Realm-level library of client access policies. A client references policies by name through its
 * `access.policies` attribute, see the client's "Access policy" tab.
 */
export const ClientAccessPolicies = () => {
  const { adminClient } = useAdminClient();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { addAlert, addError } = useAlerts();
  const { realm } = useRealm();
  const [policies, setPolicies] = useState<ComponentRepresentation[]>([]);
  const [selectedPolicy, setSelectedPolicy] =
    useState<ComponentRepresentation>();
  const [isAddDialogOpen, toggleAddDialog] = useToggle();
  const [key, setKey] = useState(0);
  const refresh = () => setKey(key + 1);

  useFetch(
    () => adminClient.components.find({ type: CLIENT_ACCESS_POLICY_TYPE }),
    setPolicies,
    [key],
  );

  const [toggleDeleteDialog, DeleteConfirm] = useConfirmDialog({
    titleKey: "clientAccessPolicyDeleteConfirmTitle",
    messageKey: t("clientAccessPolicyDeleteConfirm", {
      name: selectedPolicy?.name,
    }),
    continueButtonLabel: "delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.components.del({ id: selectedPolicy!.id! });
        addAlert(t("clientAccessPolicyDeleteSuccess"));
        setSelectedPolicy(undefined);
        refresh();
      } catch (error) {
        addError("clientAccessPolicyDeleteError", error);
      }
    },
  });

  return (
    <>
      {isAddDialogOpen && (
        <ComponentTypeDialog
          componentType={CLIENT_ACCESS_POLICY_TYPE}
          title={t("chooseClientAccessPolicyType")}
          onConfirm={(providerId) =>
            void navigate(toNewClientAccessPolicy({ realm, providerId }))
          }
          toggleDialog={toggleAddDialog}
        />
      )}
      <DeleteConfirm />
      <KeycloakDataTable
        key={key}
        ariaLabelKey="clientAccessPolicies"
        searchPlaceholderKey="searchClientAccessPolicies"
        data-testid="clientAccessPolicies"
        loader={policies}
        toolbarItem={
          <ToolbarItem>
            <Button
              data-testid="createClientAccessPolicy"
              onClick={toggleAddDialog}
            >
              {t("createClientAccessPolicy")}
            </Button>
          </ToolbarItem>
        }
        actions={[
          {
            title: t("delete"),
            onRowClick: (policy) => {
              setSelectedPolicy(policy);
              toggleDeleteDialog();
            },
          } as Action<ComponentRepresentation>,
        ]}
        columns={[
          { name: "name", displayKey: "name", cellRenderer: DetailLink },
          { name: "providerId", displayKey: "type" },
        ]}
        emptyState={
          <ListEmptyState
            message={t("noClientAccessPolicies")}
            instructions={t("noClientAccessPoliciesInstructions")}
            primaryActionText={t("createClientAccessPolicy")}
            onPrimaryAction={toggleAddDialog}
          />
        }
      />
    </>
  );
};
