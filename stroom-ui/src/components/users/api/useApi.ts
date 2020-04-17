/* eslint camelcase: ["error", {properties: "never"}]*/
/*
 * Copyright 2019 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { useCallback } from "react";
import { Account } from "../types";
import useHttpClient from "lib/useHttpClient";
import useServiceUrl from "startup/config/useServiceUrl";
import { ResultPage } from "./types";

interface Api {
  add: (account: Account) => Promise<void>;
  change: (account: Account) => Promise<void>;
  fetch: (accountId: string) => Promise<Account>;
  remove: (accountId: string) => Promise<void>;
  search: (email?: string) => Promise<ResultPage<Account>>;
}

export const useApi = (): Api => {
  const {
    httpPutEmptyResponse,
    httpGetJson,
    httpPostJsonResponse,
    httpDeleteEmptyResponse,
  } = useHttpClient();

  const { accountServiceUrl } = useServiceUrl();

  const change = useCallback(
    account =>
      httpPutEmptyResponse(`${accountServiceUrl}/${account.id}`, {
        body: JSON.stringify({
          email: account.email,
          password: account.password,
          firstName: account.firstName,
          lastName: account.lastName,
          comments: account.comments,
          enabled: account.enabled,
          inactive: account.inactive,
          locked: account.locked,
          processingAccount: account.processingAccount,
          neverExpires: account.neverExpires,
          forcePasswordChange: account.forcePasswordChange,
        }),
      }),
    [accountServiceUrl, httpPutEmptyResponse],
  );

  const add = useCallback(
    account =>
      httpPostJsonResponse(accountServiceUrl, {
        body: JSON.stringify({
          firstName: account.firstName,
          lastName: account.lastName,
          email: account.email,
          password: account.password,
          comments: account.comments,
          forcePasswordChange: account.forcePasswordChange,
          neverExpires: account.neverExpires,
        }),
      }),
    [accountServiceUrl, httpPostJsonResponse],
  );

  /**
   * Delete user
   */
  const remove = useCallback(
    (accountId: string) =>
      httpDeleteEmptyResponse(`${accountServiceUrl}/${accountId}`, {}),
    [accountServiceUrl, httpDeleteEmptyResponse],
  );

  /**
   * Fetch a user
   */
  const fetch = useCallback(
    (accountId: string) => httpGetJson(`${accountServiceUrl}/${accountId}`),
    [accountServiceUrl, httpGetJson],
  );

  const search = useCallback(
    (email: string) => httpGetJson(`${accountServiceUrl}`),
    [accountServiceUrl, httpGetJson],
  );

  return {
    add,
    fetch,
    remove,
    change,
    search,
  };
};

export default useApi;
