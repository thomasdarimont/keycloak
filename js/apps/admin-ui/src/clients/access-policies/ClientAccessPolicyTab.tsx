import { fetchWithError } from "@keycloak/keycloak-admin-client";
import type ClientRepresentation from "@keycloak/keycloak-admin-client/lib/defs/clientRepresentation";
import type ComponentRepresentation from "@keycloak/keycloak-admin-client/lib/defs/componentRepresentation";
import {
  HelpItem,
  KeycloakSelect,
  SelectVariant,
  useFetch,
} from "@keycloak/keycloak-ui-shared";
import {
  ActionGroup,
  Button,
  FormGroup,
  PageSection,
  SelectOption,
  Text,
  TextContent,
} from "@patternfly/react-core";
import { useState } from "react";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { useAdminClient } from "../../admin-client";
import { FormAccess } from "../../components/form/FormAccess";
import { useRealm } from "../../context/realm-context/RealmContext";
import { addTrailingSlash, convertAttributeNameToForm } from "../../util";
import { getAuthorizationHeaders } from "../../utils/getAuthorizationHeaders";
import type { FormFields, SaveOptions } from "../ClientDetails";
import { toClients } from "../routes/Clients";
import { CLIENT_ACCESS_POLICIES_ATTRIBUTE } from "./constants";

type ClientAccessPolicyTabProps = {
  save: (options?: SaveOptions) => void;
  client: ClientRepresentation;
};

const toList = (value: unknown): string[] =>
  typeof value === "string"
    ? value
        .split(/[,\s]+/)
        .map((v) => v.trim())
        .filter((v) => v.length > 0)
    : [];

/**
 * Lets an application manager pick which realm-level client access policies apply to this client.
 * The selection is stored in the client's `access.policies` attribute and saved with the client.
 */
export const ClientAccessPolicyTab = ({
  save,
  client,
}: ClientAccessPolicyTabProps) => {
  const { adminClient } = useAdminClient();
  const { t } = useTranslation();
  const { realm } = useRealm();
  const { control } = useFormContext<FormFields>();
  const [policies, setPolicies] = useState<ComponentRepresentation[]>([]);
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState("");

  useFetch(
    async () => {
      const response = await fetchWithError(
        `${addTrailingSlash(adminClient.baseUrl)}admin/realms/${realm}/client-access-policies`,
        {
          method: "GET",
          headers: getAuthorizationHeaders(await adminClient.getAccessToken()),
        },
      );
      return (await response.json()) as ComponentRepresentation[];
    },
    setPolicies,
    [],
  );

  const fieldName = convertAttributeNameToForm<FormFields>(
    `attributes.${CLIENT_ACCESS_POLICIES_ATTRIBUTE}`,
  );
  const names = policies.map((p) => p.name!);

  return (
    <PageSection variant="light">
      <TextContent className="pf-v5-u-mb-lg">
        <Text>{t("clientAccessPolicyTabHelp")}</Text>
        <Text>
          <Link to={toClients({ realm, tab: "access-policies" })}>
            {t("manageClientAccessPolicies")}
          </Link>
        </Text>
      </TextContent>
      <FormAccess
        role="manage-clients"
        fineGrainedAccess={client.access?.configure}
        isHorizontal
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <FormGroup
          label={t("clientAccessPolicies")}
          fieldId="clientAccessPolicies"
          labelIcon={
            <HelpItem
              helpText={t("clientAccessPoliciesSelectHelp")}
              fieldLabelId="clientAccessPolicies"
            />
          }
        >
          <Controller
            name={fieldName}
            defaultValue=""
            control={control}
            render={({ field }) => {
              const selected = toList(field.value);
              const unknown = selected.filter((s) => !names.includes(s));
              return (
                <KeycloakSelect
                  toggleId="clientAccessPolicies"
                  data-testid="clientAccessPolicies"
                  variant={SelectVariant.typeaheadMulti}
                  chipGroupProps={{
                    numChips: 5,
                    expandedText: t("hide"),
                    collapsedText: t("showRemaining"),
                  }}
                  typeAheadAriaLabel={t("clientAccessPolicies")}
                  placeholderText={t("selectClientAccessPolicies")}
                  onToggle={setOpen}
                  isOpen={open}
                  selections={selected}
                  onSelect={(value) => {
                    const name = value.toString();
                    if (!name) return;
                    const next = selected.includes(name)
                      ? selected.filter((s) => s !== name)
                      : [...selected, name];
                    field.onChange(next.join(","));
                    setFilter("");
                  }}
                  onClear={() => {
                    field.onChange("");
                    setFilter("");
                  }}
                  onFilter={setFilter}
                >
                  {[...names, ...unknown]
                    .filter((name) =>
                      name.toLowerCase().includes(filter.toLowerCase()),
                    )
                    .map((name) => (
                      <SelectOption
                        key={name}
                        value={name}
                        description={
                          unknown.includes(name)
                            ? t("clientAccessPolicyUnknown")
                            : undefined
                        }
                      >
                        {name}
                      </SelectOption>
                    ))}
                </KeycloakSelect>
              );
            }}
          />
        </FormGroup>
        <ActionGroup>
          <Button
            variant="primary"
            type="submit"
            data-testid="clientAccessPolicySave"
          >
            {t("save")}
          </Button>
        </ActionGroup>
      </FormAccess>
    </PageSection>
  );
};
