/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

import * as React from "react";
import { useQuery } from "@apollo/react-hooks";
import { Loading } from "use-patternfly";
import { IAddressResponse } from "types/ResponseTypes";
import {
  RETURN_ALL_ADDRESS_FOR_ADDRESS_SPACE,
  DELETE_ADDRESS,
  EDIT_ADDRESS,
  PURGE_ADDRESS
} from "graphql-module/queries";
import { AddressList, IAddress } from "modules/address/components/AddressList";
import { getFilteredValue } from "components/common/ConnectionListFormatter";
import { Modal, Button } from "@patternfly/react-core";
import { EmptyAddress } from "modules/address/components/EmptyAddress";
import { EditAddress } from "modules/address/containers/EditAddressPage";
import { DialoguePrompt } from "components/common/DialoguePrompt";
import { ISortBy } from "@patternfly/react-table";
import { FetchPolicy, POLL_INTERVAL } from "constants/constants";
import { useMutationQuery } from "hooks";
import { compareTwoAddress } from "../utils/util";

export interface IAddressListPageProps {
  name?: string;
  namespace?: string;
  addressSpaceType?: string;
  filterNames?: any[];
  typeValue?: string | null;
  statusValue?: string | null;
  page: number;
  perPage: number;
  setTotalAddress: (total: number) => void;
  addressSpacePlan: string | null;
  sortValue?: ISortBy;
  setSortValue: (value: ISortBy) => void;
  isWizardOpen: boolean;
  setIsWizardOpen: (value: boolean) => void;
  onCreationRefetch?: boolean;
  setOnCreationRefetch: (value: boolean) => void;
  selectedAddresses: Array<IAddress>;
  onSelectAddress: (address: IAddress, isSelected: boolean) => void;
  onSelectAllAddress: (addresses: IAddress[], isSelected: boolean) => void;
}

export const AddressListPage: React.FunctionComponent<IAddressListPageProps> = ({
  name,
  namespace,
  addressSpaceType,
  filterNames,
  typeValue,
  statusValue,
  setTotalAddress,
  page,
  perPage,
  addressSpacePlan,
  sortValue,
  setSortValue,
  isWizardOpen,
  setIsWizardOpen,
  onCreationRefetch,
  setOnCreationRefetch,
  selectedAddresses,
  onSelectAddress,
  onSelectAllAddress
}) => {
  const [
    addressBeingEdited,
    setAddressBeingEdited
  ] = React.useState<IAddress | null>();

  const [
    addressBeingDeleted,
    setAddressBeingDeleted
  ] = React.useState<IAddress | null>();

  const [
    addressBeingPurged,
    setAddressBeingPurged
  ] = React.useState<IAddress | null>();

  const [sortBy, setSortBy] = React.useState<ISortBy>();

  const resetEditFormState = () => {
    refetch();
    setAddressBeingEdited(null);
  };

  const resetDeleteFormState = () => {
    refetch();
    setAddressBeingDeleted(null);
  };

  const resetPurgeFormState = () => {
    refetch();
    setAddressBeingPurged(null);
  };

  const [setEditAddressQueryVariables] = useMutationQuery(
    EDIT_ADDRESS,
    resetEditFormState,
    resetEditFormState
  );
  const [setDeleteAddressQueryVariablse] = useMutationQuery(
    DELETE_ADDRESS,
    resetDeleteFormState,
    resetDeleteFormState
  );
  const [setPurgeAddressQueryVariables] = useMutationQuery(
    PURGE_ADDRESS,
    resetPurgeFormState,
    resetPurgeFormState
  );

  if (sortValue && sortBy !== sortValue) {
    setSortBy(sortValue);
  }
  const { data, refetch, loading } = useQuery<IAddressResponse>(
    RETURN_ALL_ADDRESS_FOR_ADDRESS_SPACE(
      page,
      perPage,
      name,
      namespace,
      filterNames,
      typeValue,
      statusValue,
      sortBy
    ),
    { pollInterval: POLL_INTERVAL, fetchPolicy: FetchPolicy.NETWORK_ONLY }
  );

  if (onCreationRefetch) {
    refetch();
    setOnCreationRefetch(false);
  }

  if (loading) return <Loading />;

  const { addresses } = data || {
    addresses: { total: 0, addresses: [] }
  };
  setTotalAddress(addresses.total);
  const addressesList: IAddress[] = addresses.addresses.map(address => ({
    name: address.metadata.name,
    displayName: address.spec.address,
    namespace: address.metadata.namespace,
    type: address.spec.type,
    planLabel: address.spec.plan.spec.displayName,
    planValue: address.spec.plan.metadata.name,
    messageIn: getFilteredValue(address.metrics, "enmasse_messages_in"),
    messageOut: getFilteredValue(address.metrics, "enmasse_messages_out"),
    storedMessages: getFilteredValue(
      address.metrics,
      "enmasse_messages_stored"
    ),
    senders: getFilteredValue(address.metrics, "enmasse_senders"),
    receivers: getFilteredValue(address.metrics, "enmasse_receivers"),
    partitions:
      address.status && address.status.planStatus
        ? address.status.planStatus.partitions
        : null,
    isReady: address.status && address.status.isReady,
    creationTimestamp: address.metadata.creationTimestamp,
    status: address.status && address.status.phase ? address.status.phase : "",
    errorMessages:
      address.status && address.status.messages ? address.status.messages : [],
    selected:
      selectedAddresses.filter(({ name, namespace }) =>
        compareTwoAddress(
          name,
          address.metadata.name,
          namespace,
          address.metadata.namespace
        )
      ).length === 1
  }));

  const handleEdit = (data: IAddress) => {
    if (!addressBeingEdited) {
      setAddressBeingEdited(data);
    }
  };
  const handlePurge = (data: IAddress) => {
    if (!addressBeingPurged) {
      setAddressBeingPurged(data);
    }
  };
  const handleCancelEdit = () => setAddressBeingEdited(null);

  const handleSaving = () => {
    if (addressBeingEdited && addressSpaceType) {
      const variables = {
        a: {
          name: addressBeingEdited.name,
          namespace: addressBeingEdited.namespace
        },
        jsonPatch:
          '[{"op":"replace","path":"/spec/plan","value":"' +
          addressBeingEdited.planValue +
          '"}]',
        patchType: "application/json-patch+json"
      };
      setEditAddressQueryVariables(variables);
    }
  };

  const handlePlanChange = (plan: string) => {
    if (addressBeingEdited) {
      addressBeingEdited.planValue = plan;
      setAddressBeingEdited({ ...addressBeingEdited });
    }
  };
  const handleCancelDelete = () => setAddressBeingDeleted(null);
  const handleCancelPurge = () => setAddressBeingPurged(null);
  const handleDelete = async () => {
    if (addressBeingDeleted) {
      const variables = {
        a: {
          name: addressBeingDeleted.name,
          namespace: addressBeingDeleted.namespace
        }
      };
      setDeleteAddressQueryVariablse(variables);
    }
  };

  const handlePurgeChange = async () => {
    if (addressBeingPurged) {
      const variables = {
        a: {
          name: addressBeingPurged.name,
          namespace: addressBeingPurged.namespace
        }
      };
      setPurgeAddressQueryVariables(variables);
    }
  };
  const handleDeleteChange = (address: IAddress) => {
    setAddressBeingDeleted(address);
  };
  const onSort = (_event: any, index: any, direction: any) => {
    setSortBy({ index: index, direction: direction });
    setSortValue({ index: index, direction: direction });
  };

  return (
    <>
      <AddressList
        rowsData={addressesList ? addressesList : []}
        onEdit={handleEdit}
        onDelete={handleDeleteChange}
        onPurge={handlePurge}
        sortBy={sortBy}
        onSort={onSort}
        onSelectAddress={onSelectAddress}
        onSelectAllAddress={onSelectAllAddress}
      />
      {addresses.total > 0 ? (
        " "
      ) : (
        <EmptyAddress
          isWizardOpen={isWizardOpen}
          setIsWizardOpen={setIsWizardOpen}
        />
      )}
      {addressBeingEdited && (
        <Modal
          id="al-modal-edit-address"
          title="Edit"
          isSmall
          isOpen={true}
          onClose={handleCancelEdit}
          actions={[
            <Button
              key="confirm"
              id="al-edit-confirm"
              variant="primary"
              onClick={handleSaving}
            >
              Confirm
            </Button>,
            <Button
              key="cancel"
              id="al-edit-cancel"
              variant="link"
              onClick={handleCancelEdit}
            >
              Cancel
            </Button>
          ]}
          isFooterLeftAligned
        >
          <EditAddress
            name={addressBeingEdited.name}
            type={addressBeingEdited.type}
            plan={addressBeingEdited.planValue}
            addressSpacePlan={addressSpacePlan}
            onChange={handlePlanChange}
          />
        </Modal>
      )}
      {addressBeingDeleted && (
        <DialoguePrompt
          option="Delete"
          detail={`Are you sure you want to delete this address: ${addressBeingDeleted.displayName} ?`}
          names={[addressBeingDeleted.name]}
          header="Delete this Address  ?"
          handleCancelDialogue={handleCancelDelete}
          handleConfirmDialogue={handleDelete}
        />
      )}
      {addressBeingPurged && (
        <DialoguePrompt
          option="Purge"
          detail={`Are you sure you want to purge this address: ${addressBeingPurged.displayName} ?`}
          names={[addressBeingPurged.name]}
          header="Purge this Address  ?"
          handleCancelDialogue={handleCancelPurge}
          handleConfirmDialogue={handlePurgeChange}
        />
      )}
    </>
  );
};
