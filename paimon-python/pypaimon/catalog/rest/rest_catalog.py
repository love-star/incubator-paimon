"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""
from pathlib import Path
from typing import List, Dict, Optional, Union

from pypaimon import Database, Catalog, Schema
from pypaimon.api import RESTApi, CatalogOptions
from pypaimon.api.api_response import PagedList, GetTableResponse
from pypaimon.common.core_options import CoreOptions
from pypaimon.common.identifier import Identifier
from pypaimon.api.options import Options


from pypaimon.catalog.catalog_context import CatalogContext
from pypaimon.catalog.catalog_utils import CatalogUtils
from pypaimon.catalog.property_change import PropertyChange
from pypaimon.catalog.table_metadata import TableMetadata
from pypaimon.catalog.rest.rest_token_file_io import RESTTokenFileIO
from pypaimon.table.file_store_table import FileStoreTable


class RESTCatalog(Catalog):
    def __init__(self, context: CatalogContext, config_required: Optional[bool] = True):
        self.api = RESTApi(context.options.to_map(), config_required)
        self.context = CatalogContext.create(Options(self.api.options), context.hadoop_conf, context.prefer_io_loader,
                                             context.fallback_io_loader)
        self.data_token_enabled = self.api.options.get(CatalogOptions.DATA_TOKEN_ENABLED)

    def list_databases(self) -> List[str]:
        return self.api.list_databases()

    def list_databases_paged(self, max_results: Optional[int] = None, page_token: Optional[str] = None,
                             database_name_pattern: Optional[str] = None) -> PagedList[str]:
        return self.api.list_databases_paged(max_results, page_token, database_name_pattern)

    def create_database(self, name: str, ignore_if_exists: bool, properties: Dict[str, str] = None):
        self.api.create_database(name, properties)

    def get_database(self, name: str) -> Database:
        response = self.api.get_database(name)
        options = response.options
        options[Catalog.DB_LOCATION_PROP] = response.location
        response.put_audit_options_to(options)
        if response is not None:
            return Database(name, options)

    def drop_database(self, name: str):
        self.api.drop_database(name)

    def alter_database(self, name: str, changes: List[PropertyChange]):
        set_properties, remove_keys = PropertyChange.get_set_properties_to_remove_keys(changes)
        self.api.alter_database(name, list(remove_keys), set_properties)

    def list_tables(self, database_name: str) -> List[str]:
        return self.api.list_tables(database_name)

    def list_tables_paged(
            self,
            database_name: str,
            max_results: Optional[int] = None,
            page_token: Optional[str] = None,
            table_name_pattern: Optional[str] = None
    ) -> PagedList[str]:
        return self.api.list_tables_paged(
            database_name,
            max_results,
            page_token,
            table_name_pattern
        )

    def get_table(self, identifier: Union[str, Identifier]) -> FileStoreTable:
        if not isinstance(identifier, Identifier):
            identifier = Identifier.from_string(identifier)
        return CatalogUtils.load_table(
            identifier,
            lambda path: self.file_io_for_data(path, identifier),
            self.file_io_from_options,
            self.load_table_metadata,
        )

    def create_table(self, identifier: Union[str, Identifier], schema: Schema, ignore_if_exists: bool):
        raise ValueError("Not implemented")

    def load_table_metadata(self, identifier: Identifier) -> TableMetadata:
        response = self.api.get_table(identifier)
        return self.to_table_metadata(identifier.get_database_name(), response)

    def to_table_metadata(self, db: str, response: GetTableResponse) -> TableMetadata:
        schema = response.get_schema()
        options: Dict[str, str] = dict(schema.options)
        options[CoreOptions.PATH] = response.get_path()
        response.put_audit_options_to(options)

        identifier = Identifier.create(db, response.get_name())
        if identifier.get_branch_name() is not None:
            options[CoreOptions.BRANCH] = identifier.get_branch_name()

        return TableMetadata(
            schema=schema.copy(options),
            is_external=response.get_is_external(),
            uuid=response.get_id()
        )

    def file_io_from_options(self, path: Path):
        return None

    def file_io_for_data(self, path: Path, identifier: Identifier):
        return RESTTokenFileIO(identifier, path, None, None) if self.data_token_enabled else None
