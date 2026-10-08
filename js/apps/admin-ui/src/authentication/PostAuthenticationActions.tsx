import ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import {
  KeycloakSpinner,
  ListEmptyState,
  useAlerts,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import {
  AlertVariant,
  Button,
  ButtonVariant,
  Label,
  LabelGroup,
  Switch,
  Toolbar,
  ToolbarContent,
  ToolbarItem,
} from "@patternfly/react-core";
import { CogIcon, TrashIcon } from "@patternfly/react-icons";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../admin-client";
import { POST_AUTHENTICATION_ACTION_TYPE } from "../clients/access-policies/constants";
import { ComponentTypeDialog } from "../components/component-type-dialog/ComponentTypeDialog";
import { useConfirmDialog } from "../components/confirm-dialog/ConfirmDialog";
import { toKey } from "../util";
import useToggle from "../utils/useToggle";
import { DraggableTable } from "./components/DraggableTable";
import { PostAuthenticationActionModal } from "./components/PostAuthenticationActionModal";

type Row = {
  name: string;
  enabled: boolean;
  triggers: string[];
  data: ComponentRepresentation;
};

const configValue = (component: ComponentRepresentation, key: string) =>
  component.config?.[key]?.[0];

const priorityOf = (component: ComponentRepresentation) =>
  parseInt(configValue(component, "priority") || "0", 10) || 0;

/**
 * Realm-wide actions that run after a user or service account authenticated for a client, ordered by priority.
 * Unlike required actions they never interact with the user: they let the request proceed or deny it.
 */
export const PostAuthenticationActions = () => {
  const { adminClient } = useAdminClient();
  const { t } = useTranslation();
  const { addAlert, addError } = useAlerts();

  const [actions, setActions] = useState<Row[]>();
  const [selectedAction, setSelectedAction] =
    useState<ComponentRepresentation>();
  const [actionToDelete, setActionToDelete] =
    useState<ComponentRepresentation>();
  const [isAddDialogOpen, toggleAddDialog] = useToggle();
  const [key, setKey] = useState(0);
  const refresh = () => setKey(key + 1);

  useFetch(
    () =>
      adminClient.components.find({ type: POST_AUTHENTICATION_ACTION_TYPE }),
    (components) =>
      setActions(
        components
          .sort((a, b) => priorityOf(a) - priorityOf(b))
          .map((component) => ({
            name: component.name!,
            enabled: configValue(component, "enabled") !== "false",
            triggers: ([] as string[]).concat(component.config?.triggers || []),
            data: component,
          })),
      ),
    [key],
  );

  const updateConfig = async (
    component: ComponentRepresentation,
    values: Record<string, string>,
  ) => {
    const config = { ...component.config };
    Object.entries(values).forEach(([k, v]) => (config[k] = [v]));
    await adminClient.components.update(
      { id: component.id! },
      { ...component, config },
    );
  };

  const toggleEnabled = async (row: Row) => {
    try {
      await updateConfig(row.data, { enabled: String(!row.enabled) });
      refresh();
      addAlert(t("postAuthenticationActionSaveSuccess"), AlertVariant.success);
    } catch (error) {
      addError("postAuthenticationActionSaveError", error);
    }
  };

  const reorder = async (newOrder: string[]) => {
    try {
      for (const [index, name] of newOrder.entries()) {
        const row = actions!.find((a) => a.name === name)!;
        const priority = (index + 1) * 10;
        if (priorityOf(row.data) !== priority) {
          await updateConfig(row.data, { priority: String(priority) });
        }
      }
      refresh();
      addAlert(t("postAuthenticationActionSaveSuccess"), AlertVariant.success);
    } catch (error) {
      addError("postAuthenticationActionSaveError", error);
    }
  };

  const [toggleDeleteDialog, DeleteConfirm] = useConfirmDialog({
    titleKey: "postAuthenticationActionDeleteConfirmTitle",
    messageKey: t("postAuthenticationActionDeleteConfirm", {
      name: actionToDelete?.name,
    }),
    continueButtonLabel: "delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.components.del({ id: actionToDelete!.id! });
        addAlert(t("postAuthenticationActionDeleteSuccess"));
        setActionToDelete(undefined);
        refresh();
      } catch (error) {
        addError("postAuthenticationActionDeleteError", error);
      }
    },
  });

  if (!actions) {
    return <KeycloakSpinner />;
  }

  return (
    <>
      {isAddDialogOpen && (
        <ComponentTypeDialog
          componentType={POST_AUTHENTICATION_ACTION_TYPE}
          title={t("choosePostAuthenticationActionType")}
          onConfirm={(providerId) => setSelectedAction({ providerId })}
          toggleDialog={toggleAddDialog}
        />
      )}
      {selectedAction && (
        <PostAuthenticationActionModal
          action={selectedAction}
          onClose={(saved) => {
            setSelectedAction(undefined);
            if (saved) refresh();
          }}
        />
      )}
      <DeleteConfirm />
      <Toolbar>
        <ToolbarContent>
          <ToolbarItem>
            <Button
              data-testid="addPostAuthenticationAction"
              onClick={toggleAddDialog}
            >
              {t("addPostAuthenticationAction")}
            </Button>
          </ToolbarItem>
        </ToolbarContent>
      </Toolbar>
      {actions.length === 0 ? (
        <ListEmptyState
          message={t("noPostAuthenticationActions")}
          instructions={t("noPostAuthenticationActionsInstructions")}
          primaryActionText={t("addPostAuthenticationAction")}
          onPrimaryAction={toggleAddDialog}
        />
      ) : (
        <DraggableTable
          keyField="name"
          onDragFinish={(_dragged, items) => void reorder(items)}
          columns={[
            {
              name: "name",
              displayKey: "action",
              width: 30,
            },
            {
              name: "providerId",
              displayKey: "type",
              cellRenderer: (row) => row.data.providerId,
              width: 20,
            },
            {
              name: "triggers",
              displayKey: "postAuthenticationTriggers",
              thTooltipText: "postAuthenticationTriggersHelp",
              cellRenderer: (row) =>
                row.triggers.length === 0 ? (
                  <Label isCompact>{t("allTriggers")}</Label>
                ) : (
                  <LabelGroup>
                    {row.triggers.map((trigger) => (
                      <Label key={trigger} isCompact>
                        {t(`postAuthenticationTrigger.${trigger}`)}
                      </Label>
                    ))}
                  </LabelGroup>
                ),
              width: 30,
            },
            {
              name: "enabled",
              displayKey: "enabled",
              cellRenderer: (row) => (
                <Switch
                  id={`enable-${toKey(row.name)}`}
                  label={t("on")}
                  labelOff={t("off")}
                  isChecked={row.enabled}
                  onChange={() => void toggleEnabled(row)}
                  aria-label={row.name}
                />
              ),
              width: 10,
            },
            {
              name: "config",
              displayKey: "configure",
              cellRenderer: (row) => (
                <>
                  <Button
                    variant="plain"
                    aria-label={t("settings")}
                    onClick={() => setSelectedAction(row.data)}
                  >
                    <CogIcon />
                  </Button>
                  <Button
                    variant="plain"
                    aria-label={t("delete")}
                    onClick={() => {
                      setActionToDelete(row.data);
                      toggleDeleteDialog();
                    }}
                  >
                    <TrashIcon />
                  </Button>
                </>
              ),
              width: 10,
            },
          ]}
          data={actions}
        />
      )}
    </>
  );
};
