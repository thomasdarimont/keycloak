import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../utils/generateEncodedPath";
import type { AppRouteObject } from "../../routes";

export type ClientAccessPolicyParams = {
  realm: string;
  id: string;
};

export type NewClientAccessPolicyParams = {
  realm: string;
  providerId: string;
};

const ClientAccessPolicyDetails = lazy(
  () => import("../access-policies/ClientAccessPolicyDetails"),
);

export const NewClientAccessPolicyRoute: AppRouteObject = {
  path: "/:realm/clients/access-policies/new",
  element: <ClientAccessPolicyDetails />,
  handle: {
    access: "manage-clients",
    breadcrumb: (t) => t("createClientAccessPolicy"),
  },
};

export const ClientAccessPolicyRoute: AppRouteObject = {
  ...NewClientAccessPolicyRoute,
  path: "/:realm/clients/access-policies/:id",
  handle: {
    access: "view-clients",
    breadcrumb: (t) => t("clientAccessPolicyDetails"),
  },
};

export const toNewClientAccessPolicy = (
  params: NewClientAccessPolicyParams,
): Partial<Path> => ({
  pathname: generateEncodedPath(NewClientAccessPolicyRoute.path, {
    realm: params.realm,
  }),
  search: `?type=${encodeURIComponent(params.providerId)}`,
});

export const toClientAccessPolicy = (
  params: ClientAccessPolicyParams,
): Partial<Path> => ({
  pathname: generateEncodedPath(ClientAccessPolicyRoute.path, params),
});
