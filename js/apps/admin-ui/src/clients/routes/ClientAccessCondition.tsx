import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../utils/generateEncodedPath";
import type { AppRouteObject } from "../../routes";

export type ClientAccessConditionParams = {
  realm: string;
  id: string;
  conditionId: string;
};

export type NewClientAccessConditionParams = {
  realm: string;
  id: string;
  conditionProviderId: string;
};

const ClientAccessConditionDetails = lazy(
  () => import("../access-policies/ClientAccessConditionDetails"),
);

export const NewClientAccessConditionRoute: AppRouteObject = {
  path: "/:realm/clients/access-policies/:id/conditions/new",
  element: <ClientAccessConditionDetails />,
  handle: {
    access: "manage-clients",
    breadcrumb: (t) => t("addClientAccessCondition"),
  },
};

export const ClientAccessConditionRoute: AppRouteObject = {
  ...NewClientAccessConditionRoute,
  path: "/:realm/clients/access-policies/:id/conditions/:conditionId",
  handle: {
    access: "view-clients",
    breadcrumb: (t) => t("clientAccessConditionDetails"),
  },
};

export const toNewClientAccessCondition = (
  params: NewClientAccessConditionParams,
): Partial<Path> => ({
  pathname: generateEncodedPath(NewClientAccessConditionRoute.path, {
    realm: params.realm,
    id: params.id,
  }),
  search: `?type=${encodeURIComponent(params.conditionProviderId)}`,
});

export const toClientAccessCondition = (
  params: ClientAccessConditionParams,
): Partial<Path> => ({
  pathname: generateEncodedPath(ClientAccessConditionRoute.path, params),
});
