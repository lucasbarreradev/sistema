package com.sistema.service;

import com.sistema.dto.ResultadoImportacion;
import com.sistema.model.Producto;
import com.sistema.repository.ProductoRepository;
import com.sistema.repository.ProductoVarianteRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ImportacionCsvServiceTest {
    @Test
    void interpretaComasDentroDeCamposEntreComillas() {
        List<String> valores = ImportacionCsvService.parsearLinea("MLA123,\"Lámpara, LED\",1250.50", ',');
        assertEquals(List.of("MLA123", "Lámpara, LED", "1250.50"), valores);
    }

    @Test
    void interpretaComillasEscapadasYPuntoYComa() {
        List<String> valores = ImportacionCsvService.parsearLinea("ABC;\"Cable \"\"premium\"\"\";10", ';');
        assertEquals(List.of("ABC", "Cable \"premium\"", "10"), valores);
    }

    @Test
    void importaExcelDePublicacionesYNoDuplicaLasVariantes() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.createSheet("Instrucciones").createRow(0).createCell(0).setCellValue("Modifica tus publicaciones");
            Sheet hoja = workbook.createSheet("Publicaciones");
            Row headers = hoja.createRow(0);
            String[] columnas = {"ITEM_ID", "VARIATION_ID", "SKU", "TITLE", "QUANTITY", "PRICE", "DESCRIPTION", "CATEGORY_ID", "BRAND", "MODEL", "GTIN", "VARIATIONS", "SIZE_VARIATION-COLUMN", "COLOR_VARIATION-COLUMN"};
            for (int i = 0; i < columnas.length; i++) headers.createCell(i).setCellValue(columnas[i]);
            Row producto = hoja.createRow(5);
            String[] valores = {"MLA123456", "", "SKU-1", "Producto de prueba", "3", "1250.50", "Descripción completa", "MLA1000", "Marca", "Modelo", "7791234567890", "", "", ""};
            for (int i = 0; i < valores.length; i++) producto.createCell(i).setCellValue(valores[i]);
            Row variante = hoja.createRow(6);
            variante.createCell(0).setCellValue("MLA123456");
            variante.createCell(1).setCellValue("9001");
            variante.createCell(2).setCellValue("SKU-1-M-N");
            variante.createCell(4).setCellValue("1");
            variante.createCell(11).setCellValue("M / Negro");
            variante.createCell(12).setCellValue("M");
            variante.createCell(13).setCellValue("Negro");
            workbook.write(output);
            excel = output.toByteArray();
        }

        ProductoRepository repository = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        ProductoVarianteRepository varianteRepository = mock(ProductoVarianteRepository.class);
        ProductoVarianteService varianteService = mock(ProductoVarianteService.class);
        when(repository.findBySkuIgnoreCase("SKU-1")).thenReturn(Optional.empty());
        doAnswer(inv -> { ((Producto) inv.getArgument(0)).setId(10L); return null; }).when(productoService).saveProducto(any());
        when(varianteRepository.findByProductoIdAndMercadoLibreVariationId(10L, "9001")).thenReturn(Optional.empty());
        ImportacionCsvService service = new ImportacionCsvService(repository, productoService,
                varianteRepository, varianteService);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "Publicaciones.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel);

        ResultadoImportacion resultado = service.importarMercadoLibre(archivo);

        assertEquals(1, resultado.getCreados());
        assertEquals(0, resultado.getActualizados());
        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoService, times(1)).saveProducto(captor.capture());
        Producto importado = captor.getValue();
        assertEquals("MLA123456", importado.getMercadoLibreId());
        assertEquals("Producto de prueba", importado.getDescripcion());
        assertEquals(3, importado.getCantidad());
        assertEquals(new BigDecimal("1250.50"), importado.getPrecioContado());
        assertEquals("MLA1000", importado.getMercadoLibreCategoriaId());
        assertEquals("Marca", importado.getMercadoLibreMarca());
        assertEquals("7791234567890", importado.getMercadoLibreGtin());
        ArgumentCaptor<com.sistema.model.ProductoVariante> varianteCaptor = ArgumentCaptor.forClass(com.sistema.model.ProductoVariante.class);
        verify(varianteService).guardarImportada(eq(importado), varianteCaptor.capture());
        assertEquals("9001", varianteCaptor.getValue().getMercadoLibreVariationId());
        assertEquals("M", varianteCaptor.getValue().getTalle());
        assertEquals("Negro", varianteCaptor.getValue().getColor());
        assertEquals(1, varianteCaptor.getValue().getStock());
    }

    @Test
    void unGuionEnVariationIdRepresentaUnProductoSimple() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet hoja = workbook.createSheet("Publicaciones");
            Row headers = hoja.createRow(0);
            String[] columnas = {"ITEM_ID", "VARIATION_ID", "TITLE", "QUANTITY", "PRICE", "CONDITION",
                    "SHIPPING_METHOD", "LOCAL_PICKUP", "STATUS", "MANUFACTURING_TIME", "LISTING_TYPE_V3",
                    "FEE_PER_SALE_MARKETPLACE_V2", "COST_OF_FINANCING_MARKETPLACE"};
            for (int i = 0; i < columnas.length; i++) headers.createCell(i).setCellValue(columnas[i]);
            Row producto = hoja.createRow(1);
            String[] valores = {"MLA2827582138", "-", "Campera deportiva", "3", "89900", "Nuevo",
                    "Mercado Envíos gratis", "Acepta", "Inactiva", "4", "Agregar cuotas", "15.83%", "8.40%"};
            for (int i = 0; i < valores.length; i++) producto.createCell(i).setCellValue(valores[i]);
            workbook.write(output);
            excel = output.toByteArray();
        }

        ProductoRepository repository = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        ProductoVarianteService varianteService = mock(ProductoVarianteService.class);
        doAnswer(inv -> { ((Producto) inv.getArgument(0)).setId(82L); return null; })
                .when(productoService).saveProducto(any());
        ImportacionCsvService service = new ImportacionCsvService(repository, productoService,
                mock(ProductoVarianteRepository.class), varianteService);

        ResultadoImportacion resultado = service.importarMercadoLibre(new MockMultipartFile("archivo", "Publicaciones.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel));

        assertEquals(1, resultado.getCreados());
        verifyNoInteractions(varianteService);
        ArgumentCaptor<Producto> productoCaptor = ArgumentCaptor.forClass(Producto.class);
        verify(productoService).saveProducto(productoCaptor.capture());
        Producto importado = productoCaptor.getValue();
        assertEquals("new", importado.getMercadoLibreCondicion());
        assertEquals("me2", importado.getMercadoLibreModoEnvio());
        assertEquals(true, importado.getMercadoLibreEnvioGratis());
        assertEquals(true, importado.getMercadoLibreRetiroPersonal());
        assertEquals("paused", importado.getMercadoLibreEstado());
        assertEquals(4, importado.getMercadoLibreTiempoDisponibilidad());
        assertEquals("Agregar cuotas", importado.getMercadoLibreConfiguracionCuotas());
    }

    @Test
    void fichaTecnicaImportaTodosLosAtributosMarcadosComoVariacion() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet hoja = workbook.createSheet("Camperas y abrigos");
            String[] columnas = {"ID", "PRODUCT_NUMBER", "VARIATION_ID", "TITLE", "SIZE_VARIATION-COLUMN",
                    "COLOR_VARIATION-COLUMN", "FABRIC_DESIGN_VARIATION-COLUMN"};
            String[] tipos = {"FIXED", "FIXED", "FIXED", "FIXED", "VARIATION", "VARIATION", "VARIATION"};
            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < columnas.length; i++) encabezado.createCell(i).setCellValue(columnas[i]);
            Row tipo = hoja.createRow(1);
            for (int i = 0; i < tipos.length; i++) tipo.createCell(i).setCellValue(tipos[i]);
            Row fila = hoja.createRow(4);
            String[] valores = {"MLA2827582138", "190121992676", "190121992676", "Campera deportiva",
                    "M", "Negra con cierre Azul", "Lisa"};
            for (int i = 0; i < valores.length; i++) fila.createCell(i).setCellValue(valores[i]);
            workbook.write(output);
            excel = output.toByteArray();
        }

        ProductoRepository repository = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        ProductoVarianteRepository varianteRepository = mock(ProductoVarianteRepository.class);
        ProductoVarianteService varianteService = mock(ProductoVarianteService.class);
        doAnswer(inv -> { ((Producto) inv.getArgument(0)).setId(82L); return null; }).when(productoService).saveProducto(any());
        ImportacionCsvService service = new ImportacionCsvService(repository, productoService, varianteRepository, varianteService);

        service.importarMercadoLibre(new MockMultipartFile("archivo", "Fichas_tecnicas.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel));

        ArgumentCaptor<com.sistema.model.ProductoVariante> captor = ArgumentCaptor.forClass(com.sistema.model.ProductoVariante.class);
        verify(varianteService).guardarImportada(any(Producto.class), captor.capture());
        assertEquals("M", captor.getValue().getTalle());
        assertEquals("Negra con cierre Azul", captor.getValue().getColor());
        assertEquals("M / Negra con cierre Azul / Lisa", captor.getValue().getNombre());
        assertEquals("{\"SIZE\":\"M\",\"COLOR\":\"Negra con cierre Azul\",\"FABRIC_DESIGN\":\"Lisa\"}",
                captor.getValue().getMercadoLibreAtributosJson());
    }

    @Test
    void agrupaComoVariantesLosItemsQueCompartenFamilyId() throws Exception {
        byte[] excel;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet hoja = workbook.createSheet("Remeras deportivas");
            String[] columnas = {"FAMILY_ID", "ID", "PRODUCT_NUMBER", "VARIATION_ID", "TITLE", "SIZE_VARIATION-COLUMN", "GTIN"};
            String[] tipos = {"FIXED", "FIXED", "FIXED", "FIXED", "FIXED", "VARIATION", "ATTRIBUTE"};
            Row encabezado = hoja.createRow(0);
            Row tipo = hoja.createRow(1);
            for (int i = 0; i < columnas.length; i++) { encabezado.createCell(i).setCellValue(columnas[i]); tipo.createCell(i).setCellValue(tipos[i]); }
            String[][] filas = {
                    {"7947787264873039", "MLA1829886677", "U4062024511", "", "Remera deportiva", "S", "1234560022045"},
                    {"7947787264873039", "MLA1829886680", "U4062024512", "", "Remera deportiva", "M", "1234560022045"}
            };
            for (int f = 0; f < filas.length; f++) {
                Row row = hoja.createRow(4 + f);
                for (int i = 0; i < filas[f].length; i++) row.createCell(i).setCellValue(filas[f][i]);
            }
            workbook.write(output);
            excel = output.toByteArray();
        }

        ProductoRepository repository = mock(ProductoRepository.class);
        ProductoService productoService = mock(ProductoService.class);
        ProductoVarianteService varianteService = mock(ProductoVarianteService.class);
        doAnswer(inv -> { ((Producto) inv.getArgument(0)).setId(90L); return null; }).when(productoService).saveProducto(any());
        ImportacionCsvService service = new ImportacionCsvService(repository, productoService,
                mock(ProductoVarianteRepository.class), varianteService);

        ResultadoImportacion resultado = service.importarMercadoLibre(new MockMultipartFile("archivo", "Fichas_tecnicas.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excel));

        assertEquals(1, resultado.getCreados());
        ArgumentCaptor<Producto> productoCaptor = ArgumentCaptor.forClass(Producto.class);
        verify(productoService).saveProducto(productoCaptor.capture());
        assertEquals("7947787264873039", productoCaptor.getValue().getMercadoLibreFamilyId());
        ArgumentCaptor<com.sistema.model.ProductoVariante> variantesCaptor = ArgumentCaptor.forClass(com.sistema.model.ProductoVariante.class);
        verify(varianteService, times(2)).guardarImportada(eq(productoCaptor.getValue()), variantesCaptor.capture());
        assertEquals(List.of("MLA1829886677", "MLA1829886680"),
                variantesCaptor.getAllValues().stream().map(com.sistema.model.ProductoVariante::getMercadoLibreItemId).toList());
        assertEquals(List.of("1234560022045", "1234560022045"),
                variantesCaptor.getAllValues().stream().map(com.sistema.model.ProductoVariante::getMercadoLibreGtin).toList());
        assertTrue(variantesCaptor.getAllValues().stream().allMatch(v -> v.getCodigoBarras() == null));
    }
}
