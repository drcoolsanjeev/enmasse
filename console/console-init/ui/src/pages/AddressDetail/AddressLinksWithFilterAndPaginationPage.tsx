/*
 * Copyright 2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */

import * as React from "react";
import {
  PageSection,
  PageSectionVariants,
  Title,
  Pagination,
  Grid,
  GridItem
} from "@patternfly/react-core";
import { GridStylesForTableHeader } from "modules/address/AddressListPage";
import { AddressLinksListPage } from "./AddressLinksListPage";
import { useHistory, useLocation } from "react-router";
import { css } from "emotion";
import { AddressLinksFilter } from "pages/AddressDetail/AddressLinksFilter";
import { ISortBy } from "@patternfly/react-table";
import { Divider } from "@patternfly/react-core/dist/js/experimental";
interface IAddressLinksWithFilterAndPaginationProps {
  addressspace_name: string;
  addressspace_namespace: string;
  addressspace_type: string;
  addressName: string;
  addressDisplayName: string;
}
export const AddressLinksWithFilterAndPagination: React.FunctionComponent<IAddressLinksWithFilterAndPaginationProps> = ({
  addressspace_name,
  addressspace_namespace,
  addressspace_type,
  addressName,
  addressDisplayName
}) => {
  const location = useLocation();
  const history = useHistory();
  const searchParams = new URLSearchParams(location.search);
  const page = parseInt(searchParams.get("page") || "", 10) || 1;
  const perPage = parseInt(searchParams.get("perPage") || "", 10) || 10;
  const [addresLinksTotal, setAddressLinksTotal] = React.useState<number>(0);
  const [filterValue, setFilterValue] = React.useState<string>("Name");
  const [filterNames, setFilterNames] = React.useState<Array<any>>([]);
  const [sortDropDownValue, setSortDropdownValue] = React.useState<ISortBy>();
  const [filterContainers, setFilterContainers] = React.useState<Array<any>>(
    []
  );
  const [filterRole, setFilterRole] = React.useState<string>();

  const setSearchParam = React.useCallback(
    (name: string, value: string) => {
      searchParams.set(name, value.toString());
    },
    [searchParams]
  );

  const handlePageChange = React.useCallback(
    (_: any, newPage: number) => {
      setSearchParam("page", newPage.toString());
      history.push({
        search: searchParams.toString()
      });
    },
    [setSearchParam, history, searchParams]
  );

  const handlePerPageChange = React.useCallback(
    (_: any, newPerPage: number) => {
      setSearchParam("page", "1");
      setSearchParam("perPage", newPerPage.toString());
      history.push({
        search: searchParams.toString()
      });
    },
    [setSearchParam, history, searchParams]
  );

  const renderPagination = (page: number, perPage: number) => {
    return (
      <Pagination
        itemCount={addresLinksTotal}
        perPage={perPage}
        page={page}
        onSetPage={handlePageChange}
        variant="top"
        onPerPageSelect={handlePerPageChange}
      />
    );
  };

  return (
    <PageSection>
      <PageSection variant={PageSectionVariants.light}>
        <Title
          size={"lg"}
          className={css(GridStylesForTableHeader.filter_left_margin)}
        >
          Links for address - {addressDisplayName}
        </Title>
        <Grid>
          <GridItem span={7}>
            <AddressLinksFilter
              filterValue={filterValue}
              setFilterValue={setFilterValue}
              filterNames={filterNames}
              setFilterNames={setFilterNames}
              filterContainers={filterContainers}
              setFilterContainers={setFilterContainers}
              filterRole={filterRole}
              setFilterRole={setFilterRole}
              totalLinks={addresLinksTotal}
              sortValue={sortDropDownValue}
              setSortValue={setSortDropdownValue}
              namespace={addressspace_namespace}
              addressName={addressName}
              addressSpaceName={addressspace_name}
            />
          </GridItem>
          <GridItem span={5}>
            {addresLinksTotal > 0 && renderPagination(page, perPage)}
          </GridItem>
        </Grid>
        <Divider />
        <AddressLinksListPage
          page={page}
          perPage={perPage}
          name={addressspace_name}
          namespace={addressspace_namespace}
          addressName={addressName}
          setAddressLinksTotal={setAddressLinksTotal}
          type={addressspace_type}
          filterNames={filterNames}
          filterContainers={filterContainers}
          sortValue={sortDropDownValue}
          setSortValue={setSortDropdownValue}
          filterRole={filterRole}
        />
        {addresLinksTotal > 0 && renderPagination(page, perPage)}
      </PageSection>
    </PageSection>
  );
};
