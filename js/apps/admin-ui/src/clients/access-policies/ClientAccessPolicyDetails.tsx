import ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import {
  Action,
  KeycloakDataTable,
  KeycloakSpinner,
  ListEmptyState,
  TextControl,
  useAlerts,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import {
  ActionGroup,
  Button,
  ButtonVariant,
  DropdownItem,
  Label,
  PageSection,
  Title,
  ToolbarItem,
} from "@patternfly/react-core";
import { useState } from "react";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAdminClient } from "../../admin-client";
import { ComponentTypeDialog } from "../../components/component-type-dialog/ComponentTypeDialog";
import { useConfirmDialog } from "../../components/confirm-dialog/ConfirmDialog";
import { DynamicComponents } from "../../components/dynamic/DynamicComponents";
import { FormAccess } from "../../components/form/FormAccess";
import { ViewHeader } from "../../components/view-header/ViewHeader";
import { useRealm } from "../../context/realm-context/RealmContext";
import { useServerInfo } from "../../context/server-info/ServerInfoProvider";
import { useParams } from "../../utils/useParams";
import useToggle from "../../utils/useToggle";
import {
  toClientAccessCondition,
  toNewClientAccessCondition,
} from "../routes/ClientAccessCondition";
import {
  ClientAccessPolicyParams,
  toClientAccessPolicy,
} from "../routes/ClientAccessPolicy";
import { toClients } from "../routes/Clients";
import {
  CLIENT_ACCESS_CONDITION_TYPE,
  CLIENT_ACCESS_POLICY_TYPE,
} from "./constants";

const toListOfPolicies = (realm: string) =>
  toClients({ realm, tab: "access-policies" });

type ConditionLinkProps = {
  condition: ComponentRepresentation;
  realm: string;
  policyId: string;
};

const ConditionLink = ({ condition, realm, policyId }: ConditionLinkProps) => (
  <Link
    to={toClientAccessCondition({
      realm,
      id: policyId,
      conditionId: condition.id!,
    })}
  >
    {condition.name}
  </Link>
);

export default function ClientAccessPolicyDetails() {
  const { adminClient } = useAdminClient();
  const { t } = useTranslation();
  const { id } = useParams<Partial<ClientAccessPolicyParams>>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { realm, realmRepresentation } = useRealm();
  const { addAlert, addError } = useAlerts();
  const serverInfo = useServerInfo();

  const form = useForm<ComponentRepresentation>({
    defaultValues: { providerId: searchParams.get("type") || "default" },
  });
  const { control, handleSubmit, reset } = form;
  const policyName = useWatch({ control, defaultValue: "", name: "name" });
  const providerId = useWatch({ control, name: "providerId" });
  const provider = serverInfo.componentTypes?.[CLIENT_ACCESS_POLICY_TYPE]?.find(
    (p) => p.id === providerId,
  );

  const [loaded, setLoaded] = useState(!id);
  const [conditions, setConditions] = useState<ComponentRepresentation[]>([]);
  const [selectedCondition, setSelectedCondition] =
    useState<ComponentRepresentation>();
  const [isAddConditionOpen, toggleAddCondition] = useToggle();
  const [key, setKey] = useState(0);
  const refresh = () => setKey(key + 1);

  useFetch(
    async (): Promise<{
      policy?: ComponentRepresentation;
      conditions: ComponentRepresentation[];
    }> => {
      if (!id) return { conditions: [] };
      const [policy, conditions] = await Promise.all([
        adminClient.components.findOne({ id }),
        adminClient.components.find({
          parent: id,
          type: CLIENT_ACCESS_CONDITION_TYPE,
        }),
      ]);
      return { policy, conditions };
    },
    ({ policy, conditions }) => {
      if (policy) reset(policy);
      setConditions(conditions);
      setLoaded(true);
    },
    [key],
  );

  const onSubmit = async (component: ComponentRepresentation) => {
    if (component.config) {
      Object.entries(component.config).forEach(
        ([k, v]) => (component.config![k] = Array.isArray(v) ? v : [v]),
      );
    }
    const updated: ComponentRepresentation = {
      ...component,
      parentId: realmRepresentation.id,
      providerType: CLIENT_ACCESS_POLICY_TYPE,
      providerId,
    };
    try {
      if (id) {
        await adminClient.components.update({ id }, updated);
      } else {
        const { id: newId } = await adminClient.components.create(updated);
        void navigate(toClientAccessPolicy({ realm, id: newId }));
      }
      addAlert(
        t(
          id
            ? "clientAccessPolicySaveSuccess"
            : "clientAccessPolicyCreateSuccess",
        ),
      );
    } catch (error) {
      addError("clientAccessPolicySaveError", error);
    }
  };

  const [toggleDeletePolicyDialog, DeletePolicyConfirm] = useConfirmDialog({
    titleKey: "clientAccessPolicyDeleteConfirmTitle",
    messageKey: t("clientAccessPolicyDeleteConfirm", { name: policyName }),
    continueButtonLabel: "delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.components.del({ id: id! });
        addAlert(t("clientAccessPolicyDeleteSuccess"));
        void navigate(toListOfPolicies(realm));
      } catch (error) {
        addError("clientAccessPolicyDeleteError", error);
      }
    },
  });

  const [toggleDeleteConditionDialog, DeleteConditionConfirm] =
    useConfirmDialog({
      titleKey: "clientAccessConditionDeleteConfirmTitle",
      messageKey: t("clientAccessConditionDeleteConfirm", {
        name: selectedCondition?.name,
      }),
      continueButtonLabel: "delete",
      continueButtonVariant: ButtonVariant.danger,
      onConfirm: async () => {
        try {
          await adminClient.components.del({ id: selectedCondition!.id! });
          addAlert(t("clientAccessConditionDeleteSuccess"));
          setSelectedCondition(undefined);
          refresh();
        } catch (error) {
          addError("clientAccessConditionDeleteError", error);
        }
      },
    });

  if (!provider || !loaded) {
    return <KeycloakSpinner />;
  }

  return (
    <>
      <ViewHeader
        titleKey={id ? policyName! : "createClientAccessPolicy"}
        subKey={
          id ? "clientAccessPolicyDetailsHelp" : "clientAccessPolicyCreateHelp"
        }
        dropdownItems={
          id
            ? [
                <DropdownItem
                  data-testid="delete"
                  key="delete"
                  onClick={toggleDeletePolicyDialog}
                >
                  {t("delete")}
                </DropdownItem>,
              ]
            : undefined
        }
      />
      <DeletePolicyConfirm />
      <DeleteConditionConfirm />
      {isAddConditionOpen && (
        <ComponentTypeDialog
          componentType={CLIENT_ACCESS_CONDITION_TYPE}
          title={t("chooseClientAccessConditionType")}
          onConfirm={(conditionProviderId) =>
            void navigate(
              toNewClientAccessCondition({
                realm,
                id: id!,
                conditionProviderId,
              }),
            )
          }
          toggleDialog={toggleAddCondition}
        />
      )}
      <PageSection variant="light">
        <FormProvider {...form}>
          <FormAccess
            role="manage-clients"
            isHorizontal
            onSubmit={handleSubmit(onSubmit)}
          >
            <TextControl name="providerId" label={t("type")} readOnly />
            <TextControl
              name="name"
              label={t("name")}
              labelIcon={t("clientAccessPolicyNameHelp")}
              rules={{ required: t("required") }}
            />
            <DynamicComponents properties={provider.properties} />
            <ActionGroup>
              <Button data-testid="save" type="submit">
                {t("save")}
              </Button>
              <Button
                data-testid="cancel"
                variant="link"
                component={(props) => (
                  <Link {...props} to={toListOfPolicies(realm)} />
                )}
              >
                {t("cancel")}
              </Button>
            </ActionGroup>
          </FormAccess>
        </FormProvider>
      </PageSection>
      {id && (
        <PageSection variant="light" className="pf-v5-u-pt-0">
          <Title headingLevel="h2" size="lg" className="pf-v5-u-mb-md">
            {t("clientAccessConditions")}
          </Title>
          <KeycloakDataTable
            key={key}
            ariaLabelKey="clientAccessConditions"
            data-testid="clientAccessConditions"
            loader={conditions}
            toolbarItem={
              <ToolbarItem>
                <Button
                  data-testid="addClientAccessCondition"
                  onClick={toggleAddCondition}
                >
                  {t("addClientAccessCondition")}
                </Button>
              </ToolbarItem>
            }
            actions={[
              {
                title: t("delete"),
                onRowClick: (condition) => {
                  setSelectedCondition(condition);
                  toggleDeleteConditionDialog();
                },
              } as Action<ComponentRepresentation>,
            ]}
            columns={[
              {
                name: "name",
                displayKey: "name",
                cellRenderer: (condition) => (
                  <ConditionLink
                    condition={condition}
                    realm={realm}
                    policyId={id!}
                  />
                ),
              },
              { name: "providerId", displayKey: "type" },
              {
                name: "negate",
                displayKey: "negate",
                cellRenderer: (condition) =>
                  String(condition.config?.negate ?? "false") === "true" ? (
                    <Label color="orange" isCompact>
                      {t("negated")}
                    </Label>
                  ) : (
                    ""
                  ),
              },
            ]}
            emptyState={
              <ListEmptyState
                message={t("noClientAccessConditions")}
                instructions={t("noClientAccessConditionsInstructions")}
                primaryActionText={t("addClientAccessCondition")}
                onPrimaryAction={toggleAddCondition}
              />
            }
          />
        </PageSection>
      )}
    </>
  );
}
