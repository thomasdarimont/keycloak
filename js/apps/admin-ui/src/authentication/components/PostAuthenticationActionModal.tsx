import ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import { TextControl, useAlerts } from "@keycloak/keycloak-ui-shared";
import {
  ActionGroup,
  AlertVariant,
  Button,
  ButtonVariant,
  Form,
  Modal,
  ModalVariant,
} from "@patternfly/react-core";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useAdminClient } from "../../admin-client";
import { POST_AUTHENTICATION_ACTION_TYPE } from "../../clients/access-policies/constants";
import { DynamicComponents } from "../../components/dynamic/DynamicComponents";
import { useRealm } from "../../context/realm-context/RealmContext";
import { useServerInfo } from "../../context/server-info/ServerInfoProvider";

type PostAuthenticationActionModalProps = {
  /** existing component to edit, or a bare `{ providerId }` to create a new one */
  action: ComponentRepresentation;
  onClose: (saved: boolean) => void;
};

export const PostAuthenticationActionModal = ({
  action,
  onClose,
}: PostAuthenticationActionModalProps) => {
  const { adminClient } = useAdminClient();
  const { t } = useTranslation();
  const { addAlert, addError } = useAlerts();
  const { realmRepresentation } = useRealm();
  const serverInfo = useServerInfo();

  const provider = serverInfo.componentTypes?.[
    POST_AUTHENTICATION_ACTION_TYPE
  ]?.find((p) => p.id === action.providerId);

  const form = useForm<ComponentRepresentation>({ defaultValues: action });
  const { handleSubmit } = form;

  const save = async (component: ComponentRepresentation) => {
    if (component.config) {
      Object.entries(component.config).forEach(
        ([key, value]) =>
          (component.config![key] = Array.isArray(value) ? value : [value]),
      );
    }
    const updated: ComponentRepresentation = {
      ...component,
      parentId: realmRepresentation.id,
      providerType: POST_AUTHENTICATION_ACTION_TYPE,
      providerId: action.providerId,
    };
    try {
      if (action.id) {
        await adminClient.components.update({ id: action.id }, updated);
      } else {
        await adminClient.components.create(updated);
      }
      addAlert(
        t(
          action.id
            ? "postAuthenticationActionSaveSuccess"
            : "postAuthenticationActionCreateSuccess",
        ),
        AlertVariant.success,
      );
      onClose(true);
    } catch (error) {
      addError("postAuthenticationActionSaveError", error);
    }
  };

  return (
    <Modal
      variant={ModalVariant.small}
      isOpen
      title={
        action.id
          ? t("postAuthenticationActionConfig", { name: action.name })
          : t("addPostAuthenticationAction")
      }
      onClose={() => onClose(false)}
    >
      <Form
        id="post-authentication-action-form"
        isHorizontal
        onSubmit={handleSubmit(save)}
      >
        <FormProvider {...form}>
          <TextControl name="providerId" label={t("provider")} readOnly />
          <TextControl
            name="name"
            label={t("name")}
            rules={{ required: t("required") }}
          />
          <DynamicComponents
            properties={provider?.properties || []}
            isNew={!action.id}
          />
        </FormProvider>
        <ActionGroup>
          <Button data-testid="save" variant="primary" type="submit">
            {t("save")}
          </Button>
          <Button
            data-testid="cancel"
            variant={ButtonVariant.link}
            onClick={() => onClose(false)}
          >
            {t("cancel")}
          </Button>
        </ActionGroup>
      </Form>
    </Modal>
  );
};
