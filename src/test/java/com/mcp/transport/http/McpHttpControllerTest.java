package com.mcp.transport.http;

import com.mcp.application.cache.RefreshMetadataCacheService;
import com.mcp.application.execution.CreateTableService;
import com.mcp.application.execution.ExecuteQueryService;
import com.mcp.application.execution.ExecuteWriteService;
import com.mcp.application.execution.ExplainQueryService;
import com.mcp.application.metadata.DescribeObjectService;
import com.mcp.application.metadata.ListDatasourcesService;
import com.mcp.application.metadata.ListIndexesAndViewsService;
import com.mcp.application.metadata.ListTablesService;
import com.mcp.application.metadata.LocateTableService;
import com.mcp.application.status.GetServiceStatusService;
import com.mcp.infrastructure.support.McpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class McpHttpControllerTest {

    @Mock private ListDatasourcesService listDatasourcesService;
    @Mock private ListTablesService listTablesService;
    @Mock private ListIndexesAndViewsService listIndexesAndViewsService;
    @Mock private LocateTableService locateTableService;
    @Mock private DescribeObjectService describeObjectService;
    @Mock private ExecuteQueryService executeQueryService;
    @Mock private ExecuteWriteService executeWriteService;
    @Mock private CreateTableService createTableService;
    @Mock private ExplainQueryService explainQueryService;
    @Mock private RefreshMetadataCacheService refreshMetadataCacheService;
    @Mock private GetServiceStatusService getServiceStatusService;

    private McpHttpController controller;

    @BeforeEach
    void setUp() {
        controller = new McpHttpController(
                listDatasourcesService,
                listTablesService,
                listIndexesAndViewsService,
                locateTableService,
                describeObjectService,
                executeQueryService,
                executeWriteService,
                createTableService,
                explainQueryService,
                refreshMetadataCacheService,
                getServiceStatusService
        );
    }

    @Test
    void executeWrite_nullBody_returnsParamMissing() {
        McpResponse response = controller.executeWrite(null);

        assertFalse(response.isSuccess());
        assertEquals("PARAM_MISSING", response.getCode());
        assertEquals("Request body is required.", response.getMessage());
        verifyNoInteractions(executeWriteService);
    }

    @Test
    void executeWrite_missingDatasource_returnsParamMissing() {
        Map<String, String> body = new HashMap<>();
        body.put("sql", "DELETE FROM t WHERE id=1");

        McpResponse response = controller.executeWrite(body);

        assertFalse(response.isSuccess());
        assertEquals("PARAM_MISSING", response.getCode());
        assertEquals("datasourceName is required.", response.getMessage());
        verifyNoInteractions(executeWriteService);
    }

    @Test
    void executeWrite_missingSql_returnsParamMissing() {
        Map<String, String> body = new HashMap<>();
        body.put("datasourceName", "main_db");

        McpResponse response = controller.executeWrite(body);

        assertFalse(response.isSuccess());
        assertEquals("PARAM_MISSING", response.getCode());
        assertEquals("sql is required.", response.getMessage());
        verifyNoInteractions(executeWriteService);
    }
}
