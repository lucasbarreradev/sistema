# 🛒 Sistema de Gestión Comercial - Tienda de Electricidad

Aplicación web completa para la gestión de una tienda de electricidad, desarrollada con Spring Boot. Permite administrar stock, ventas y flujo comercial en tiempo real. Cuenta con integración con woocommerce, tienda nube y mercado libre.



## ⚙️ Tecnologías
- Java + Spring Boot
- Spring Security (autenticación y roles)
- Spring Data JPA / Hibernate
- MySQL
- JSP + Bootstrap
- Docker
- VPS (deploy productivo)



## 🧩 Funcionalidades

- Gestión de productos, clientes y proveedores  
- Control de stock en tiempo real  
- Presupuestos → conversión a remitos → ventas  
- Reportes de ganancias y ventas  
- Generación de comprobantes  
- Autenticación con roles (admin / empleado)  



## 🧠 Lo más importante (diferencial)

- Implementación de flujo comercial completo (tipo sistema real)  
- Arquitectura en capas (Controller - Service - Repository)  
- Optimización de consultas (evitando N+1 con JOIN FETCH)  
- Manejo de transacciones con @Transactional  
- Deploy en VPS con Docker  



## 🚀 Ejecución con Docker

1. Clonar el repositorio
```bash
git clone https://github.com/lucasbarreradev/electricidad
cd proyecto
```
2. Levantar el sistema
```bash
ADMIN_PASS=1234 EMPLEADO_PASS=12345 docker-compose up --build -d
```
3. Acceder a la aplicación
```bash
http://localhost:8080/sistema
```

## ✒️ Autor
Lucas Barrera
* [LinkedIn](https://www.linkedin.com/in/lucas-barrera-dev)

## Integraciones con canales de venta

El catálogo admite una foto por producto, importación masiva desde CSV de Mercado Libre y publicación/sincronización con WooCommerce, Mercado Libre y Tiendanube.

Las conexiones se habilitan mediante variables de entorno para evitar guardar credenciales en la base de datos:

- WooCommerce: `WOOCOMMERCE_URL`, `WOOCOMMERCE_CONSUMER_KEY`, `WOOCOMMERCE_CONSUMER_SECRET`.
- Mercado Libre: `ML_CLIENT_ID`, `ML_CLIENT_SECRET`, `ML_ACCESS_TOKEN`, `ML_REFRESH_TOKEN`, `INTEGRATIONS_ENCRYPTION_KEY`, `MERCADOLIBRE_CATEGORY_ID` y, opcionalmente, `MERCADOLIBRE_LISTING_TYPE_ID`. También se conservan como alias los nombres largos `MERCADOLIBRE_*`. El flujo User Products está activo por defecto; para una cuenta legada se puede usar `MERCADOLIBRE_USER_PRODUCTS=false`.
- Tiendanube: `TIENDANUBE_STORE_ID`, `TIENDANUBE_ACCESS_TOKEN`, `TIENDANUBE_USER_AGENT`.
- Fotos: `PUBLIC_BASE_URL` debe contener la URL pública HTTPS de esta aplicación para que los canales puedan descargar la imagen.

Desde **Canales de venta** se puede importar un CSV o seleccionar productos y publicarlos en uno o más canales. Si el producto ya fue publicado, se actualiza la publicación existente.

También se puede traer el catálogo activo desde Mercado Libre, WooCommerce o Tiendanube hacia el sistema, y sincronizar un canal completo hacia uno o más destinos. El sistema funciona como catálogo central: conserva la relación entre cada producto y sus IDs externos para que las ejecuciones posteriores actualicen en lugar de duplicar. En una sincronización, el canal elegido como origen define nombre, SKU, precio, stock y foto.

El access token de Mercado Libre se renueva automáticamente antes de vencer. El administrador también puede vincular la cuenta desde **Canales de venta > Conectar cuenta**, sin copiar tokens manualmente. Para habilitarlo, configura `MERCADOLIBRE_REDIRECT_URI` con la URL pública exacta del callback (por ejemplo `https://stock.ejemplo.com/canales/mercadolibre/callback`) y registra exactamente esa misma URL en la aplicación de Mercado Libre. Los tokens rotativos se guardan cifrados en MySQL. `INTEGRATIONS_ENCRYPTION_KEY` debe ser una clave Base64 de exactamente 32 bytes y debe conservarse entre despliegues.

## Stock por webhooks

El stock de ventas externas se actualiza por webhooks, sin consultar los canales cada minuto. Al recibir una venta pagada, el sistema descuenta las unidades una sola vez y envía el stock resultante a los otros dos canales.

- Configura `PUBLIC_BASE_URL` con la URL pública HTTPS del sistema.
- Configura `INTEGRATIONS_WEBHOOK_SECRET` con un secreto aleatorio. Si se deja vacío, se usa `INTEGRATIONS_ENCRYPTION_KEY` como compatibilidad.
- WooCommerce registra automáticamente los tópicos `order.created` y `order.updated`.
- Tiendanube registra automáticamente el evento `order/paid`.
- En la aplicación de Mercado Libre hay que registrar manualmente `${PUBLIC_BASE_URL}/webhooks/mercadolibre` como URL de notificaciones y activar el tópico `orders_v2`.

Los endpoints receptores son `/webhooks/woocommerce`, `/webhooks/tiendanube` y `/webhooks/mercadolibre`. WooCommerce se valida mediante su firma HMAC; Tiendanube mediante `x-linkedstore-hmac-sha256` firmado con el Client Secret de la aplicación; y Mercado Libre mediante `application_id`, el vendedor de la orden y una consulta autenticada del recurso informado.

## Conectar cuentas desde el sistema

El administrador puede vincular las tres plataformas desde **Canales de venta**, sin copiar tokens de cada comercio:

- **WooCommerce:** ingresa la URL HTTPS de la tienda, pulsa **Conectar cuenta** y autoriza acceso de lectura/escritura en WordPress. WooCommerce envía la Consumer Key y el Consumer Secret directamente al callback `${PUBLIC_BASE_URL}/canales/woocommerce/callback`.
- **Tiendanube:** configura `TN_CLIENT_ID`, `TN_CLIENT_SECRET` y `TIENDANUBE_REDIRECT_URI`, y habilita en el Portal de Socios los permisos `read_orders` y `write_products`. Luego pulsa **Conectar cuenta** y acepta los permisos. La URL de retorno debe ser exactamente `${PUBLIC_BASE_URL}/canales/tiendanube/callback` y también debe estar registrada en el Portal. Si se agregan permisos después de haber conectado una tienda, hay que desconectarla y volverla a conectar para emitir un token con los permisos nuevos.
- **Mercado Libre:** conserva el flujo OAuth ya disponible.

Las credenciales conectadas se guardan cifradas en MySQL y tienen prioridad sobre `WC_*` y `TN_*` del `.env`. Las variables anteriores quedan como respaldo. Al conectar WooCommerce o Tiendanube también se registran sus webhooks de órdenes sin esperar un reinicio.

## Multi-tenancy

La aplicación usa una sola URL y una sola instalación para todos los negocios. El usuario inicia sesión normalmente; su usuario determina el negocio al que pertenece y el `tenant_id` queda guardado en la sesión HTTP. No se envía el tenant en la URL.

- Los nombres de usuario son únicos en toda la aplicación, porque el login no solicita un código de negocio.
- Productos, variantes, clientes, proveedores, ventas, presupuestos, gastos, remitos, movimientos, publicaciones y órdenes procesadas se filtran automáticamente por tenant.
- Los SKU, códigos comerciales e IDs externos pueden repetirse entre negocios; sus restricciones de unicidad incluyen `tenant_id`.
- Las credenciales y tokens de Mercado Libre, WooCommerce y Tiendanube se guardan por tenant y cifrados en MySQL.
- Los webhooks identifican el tenant mediante la cuenta o tienda externa que originó el evento, antes de modificar stock.
- Las tareas asíncronas propagan el tenant de la operación original.
- Las fotos públicas resuelven internamente su tenant por el ID global del producto; el tenant tampoco aparece en esas URLs.

El usuario inicial `admin` tiene el rol `SUPERADMIN`. Desde **Negocios** puede crear cada comercio junto con su primer administrador, o activar/desactivar su acceso. Cada administrador después gestiona solamente los usuarios y datos de su propio negocio.

Al actualizar una base existente, la migración crea el negocio `principal` (`tenant_id = 1`) y asigna allí todos los datos previos. Antes de la migración conviene realizar un `mysqldump`.

Las variables `ML_CLIENT_ID`, `ML_CLIENT_SECRET`, `TN_CLIENT_ID`, `TN_CLIENT_SECRET`, `PUBLIC_BASE_URL` y la clave de cifrado pertenecen a la instalación SaaS. Los access tokens y credenciales de cada comercio se obtienen conectando su cuenta desde **Canales de venta**. Los tokens del `.env` se conservan únicamente como respaldo compatible para el negocio principal.
