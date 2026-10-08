import ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import {
  KeycloakSpinner,
  TextControl,
  useAlerts,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import { ActionGroup, Button, PageSection } from "@patternfly/react-core";
import { useState } from "react";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAdminClient } from "../../admin-client";
import { DynamicComponents } from "../../components/dynamic/DynamicComponents";
import { FormAccess } from "../../components/form/FormAccess";
import { ViewHeader } from "../../components/view-header/ViewHeader";
import { useRealm } from "../../context/realm-context/RealmContext";
import { useServerInfo } from "../../context/server-info/ServerInfoProvider";
import { useParams } from "../../utils/useParams";
import { ClientAccessConditionParams } from "../routes/ClientAccessCondition";
import { toClientAccessPolicy } from "../routes/ClientAccessPolicy";
import { CLIENT_ACCESS_CONDITION_TYPE } from "./constants";

export default function ClientAccessConditionDetails() {
  const { adminClient } = useAdminClient();
  const { t } = useTranslation();
  const { id, conditionId } = useParams<Partial<ClientAccessConditionParams>>();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { realm } = useRealm();
  const { addAlert, addError } = useAlerts();
  const serverInfo = useServerInfo();

  const form = useForm<ComponentRepresentation>({
    defaultValues: { providerId: searchParams.get("type") || "" },
  });
  const { control, handleSubmit, reset } = form;
  const conditionName = useWatch({ control, defaultValue: "", name: "name" });
  const conditionProviderId = useWatch({ control, name: "providerId" });
  const provider = serverInfo.componentTypes?.[
    CLIENT_ACCESS_CONDITION_TYPE
  ]?.find((p) => p.id === conditionProviderId);
  const [loaded, setLoaded] = useState(!conditionId);

  useFetch(
    async () =>
      conditionId
        ? await adminClient.components.findOne({ id: conditionId })
        : undefined,
    (condition) => {
      if (condition) reset(condition);
      setLoaded(true);
    },
    [],
  );

  const backToPolicy = toClientAccessPolicy({ realm, id: id! });

  const onSubmit = async (component: ComponentRepresentation) => {
    if (component.config) {
      Object.entries(component.config).forEach(
        ([k, v]) => (component.config![k] = Array.isArray(v) ? v : [v]),
      );
    }
    const updated: ComponentRepresentation = {
      ...component,
      parentId: id,
      providerType: CLIENT_ACCESS_CONDITION_TYPE,
      providerId: conditionProviderId,
    };
    try {
      if (conditionId) {
        await adminClient.components.update({ id: conditionId }, updated);
      } else {
        await adminClient.components.create(updated);
      }
      addAlert(
        t(
          conditionId
            ? "clientAccessConditionSaveSuccess"
            : "clientAccessConditionCreateSuccess",
        ),
      );
      void navigate(backToPolicy);
    } catch (error) {
      addError("clientAccessConditionSaveError", error);
    }
  };

  if (!provider || !loaded) {
    return <KeycloakSpinner />;
  }

  return (
    <>
      <ViewHeader
        titleKey={conditionId ? conditionName! : "addClientAccessCondition"}
        subKey={provider.helpText}
      />
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
              rules={{ required: t("required") }}
            />
            <DynamicComponents
              properties={provider.properties}
              isNew={!conditionId}
            />
            <ActionGroup>
              <Button data-testid="save" type="submit">
                {t("save")}
              </Button>
              <Button
                data-testid="cancel"
                variant="link"
                component={(props) => <Link {...props} to={backToPolicy} />}
              >
                {t("cancel")}
              </Button>
            </ActionGroup>
          </FormAccess>
        </FormProvider>
      </PageSection>
    </>
  );
}
