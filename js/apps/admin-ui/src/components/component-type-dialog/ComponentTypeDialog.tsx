import {
  DataList,
  DataListCell,
  DataListItem,
  DataListItemCells,
  DataListItemRow,
  Modal,
  ModalVariant,
} from "@patternfly/react-core";
import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useServerInfo } from "../../context/server-info/ServerInfoProvider";
import useLocaleSort, { mapByKey } from "../../utils/useLocaleSort";

type ComponentTypeDialogProps = {
  /** fully qualified provider class name, the key into serverInfo.componentTypes */
  componentType: string;
  title: string;
  onConfirm: (providerId: string) => void;
  toggleDialog: () => void;
};

/**
 * Lets the user pick one of the registered providers of a component type.
 */
export const ComponentTypeDialog = ({
  componentType,
  title,
  onConfirm,
  toggleDialog,
}: ComponentTypeDialogProps) => {
  const { t } = useTranslation();
  const serverInfo = useServerInfo();
  const descriptions = serverInfo.componentTypes?.[componentType];
  const localeSort = useLocaleSort();

  const rows = useMemo(
    () => localeSort(descriptions || [], mapByKey("id")),
    [descriptions],
  );

  return (
    <Modal
      variant={ModalVariant.medium}
      title={title}
      isOpen
      onClose={toggleDialog}
    >
      <DataList
        onSelectDataListItem={(_event, id) => {
          onConfirm(id);
          toggleDialog();
        }}
        aria-label={title}
        isCompact
      >
        <DataListItem aria-label={t("headerName")} id="header">
          <DataListItemRow>
            <DataListItemCells
              dataListCells={[t("name"), t("description")].map((name) => (
                <DataListCell style={{ fontWeight: 700 }} key={name}>
                  {name}
                </DataListCell>
              ))}
            />
          </DataListItemRow>
        </DataListItem>
        {rows.map((provider) => (
          <DataListItem
            aria-label={provider.id}
            key={provider.id}
            data-testid={provider.id}
            id={provider.id}
          >
            <DataListItemRow>
              <DataListItemCells
                dataListCells={[
                  <DataListCell width={2} key={`name-${provider.id}`}>
                    {provider.id}
                  </DataListCell>,
                  <DataListCell width={4} key={`description-${provider.id}`}>
                    {provider.helpText}
                  </DataListCell>,
                ]}
              />
            </DataListItemRow>
          </DataListItem>
        ))}
      </DataList>
    </Modal>
  );
};
