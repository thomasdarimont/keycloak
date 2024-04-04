import RequiredActionConfigRepresentation from "@keycloak/keycloak-admin-client/lib/defs/requiredActionConfigRepresentation";
import RequiredActionConfigInfoRepresentation from "@keycloak/keycloak-admin-client/lib/defs/requiredActionConfigInfoRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  ButtonVariant,
  Form,
  Modal,
  ModalVariant,
  Tooltip,
} from "@patternfly/react-core";
import { CogIcon, TrashIcon } from "@patternfly/react-icons";
import { useEffect, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { adminClient } from "../../admin-client";
import { useAlerts } from "../../components/alert/Alerts";
import { DynamicComponents } from "../../components/dynamic/DynamicComponents";
import { convertFormValuesToObject, convertToFormValues } from "../../util";
import { useFetch } from "../../utils/useFetch";
import type RequiredActionProviderRepresentation from "@keycloak/keycloak-admin-client/lib/defs/requiredActionProviderRepresentation";

type RequiredActionConfigModalForm = {
  // alias: string;
  config: { [index: string]: string };
};

type RequiredActionConfigModalProps = {
  requiredAction: RequiredActionProviderRepresentation;
};

export const RequiredActionConfigModal = ({
  requiredAction,
}: RequiredActionConfigModalProps) => {
  const { t } = useTranslation();
  const { addAlert, addError } = useAlerts();

  const [show, setShow] = useState(false);
  const [config, setConfig] = useState<RequiredActionConfigRepresentation>();
  const [configDescription, setConfigDescription] =
    useState<RequiredActionConfigInfoRepresentation>();

  const form = useForm<RequiredActionConfigModalForm>();
  const { setValue, handleSubmit } = form;

  // // default config all required actions should have
  // const defaultConfigProperties = [];

  const setupForm = (config?: RequiredActionConfigRepresentation) => {
    convertToFormValues(config || {}, setValue);
  };

  useFetch(
    async () => {
      let config: RequiredActionConfigRepresentation | undefined;

      const configDescription = requiredAction.configurable
        ? await adminClient.authenticationManagement.getRequiredActionConfigDescription(
            {
              alias: requiredAction.providerId!,
            },
          )
        : {
            name: requiredAction.name,
            properties: [],
          };

      if (requiredAction.configurable) {
        config =
          await adminClient.authenticationManagement.getRequiredActionConfig({
            alias: requiredAction.providerId!,
          });
      }

      // merge default and fetched config properties
      configDescription.properties = [
        //...defaultConfigProperties!,
        ...configDescription.properties!,
      ];

      return { configDescription, config };
    },
    ({ configDescription, config }) => {
      setConfigDescription(configDescription);
      setConfig(config);
    },
    [],
  );

  useEffect(() => {
    if (config) setupForm(config);
  }, [config]);

  const save = async (saved: RequiredActionConfigModalForm) => {
    const changedConfig = convertFormValuesToObject(saved);
    try {
      if (config) {
        const newConfig = {
          config: changedConfig.config,
        };
        await adminClient.authenticationManagement.updateRequiredActionConfig(
          { alias: requiredAction.providerId! },
          newConfig,
        );
        setConfig({ ...newConfig });
      } else {
        const newConfig = {
          // alias: changedConfig.alias,
          config: changedConfig.config,
        };
        await adminClient.authenticationManagement.updateRequiredActionConfig(
          { alias: requiredAction.providerId! },
          newConfig,
        );
        setConfig({ ...newConfig.config });
      }
      addAlert(t("configSaveSuccess"), AlertVariant.success);
      setShow(false);
    } catch (error) {
      addError("configSaveError", error);
    }
  };

  return (
    <>
      <Tooltip content={t("settings")}>
        <Button
          variant="plain"
          aria-label={t("settings")}
          onClick={() => setShow(true)}
        >
          <CogIcon />
        </Button>
      </Tooltip>
      {configDescription && (
        <Modal
          variant={ModalVariant.small}
          isOpen={show}
          title={t("requiredActionConfig", { name: requiredAction.alias })}
          onClose={() => setShow(false)}
        >
          <Form id="required-action-config-form" onSubmit={handleSubmit(save)}>
            <FormProvider {...form}>
              <DynamicComponents
                stringify
                properties={configDescription.properties || []}
              />
            </FormProvider>
            <ActionGroup>
              <Button data-testid="save" variant="primary" type="submit">
                {t("save")}
              </Button>
              <Button
                data-testid="cancel"
                variant={ButtonVariant.link}
                onClick={() => {
                  setShow(false);
                }}
              >
                {t("cancel")}
              </Button>
              {config && (
                <Button
                  className="pf-u-ml-4xl"
                  data-testid="clear"
                  variant={ButtonVariant.link}
                  onClick={async () => {
                    await adminClient.authenticationManagement.removeRequiredActionConfig(
                      {
                        alias: requiredAction.providerId!,
                      },
                    );
                    setConfig(undefined);
                    setShow(false);
                  }}
                >
                  {t("clear")} <TrashIcon />
                </Button>
              )}
            </ActionGroup>
          </Form>
        </Modal>
      )}
    </>
  );
};
