<?php
/**
 * Buscador de neumáticos para atributos locales de WooCommerce.
 *
 * Atributos esperados:
 * - Relación de aspecto
 * - Ancho de sección
 * - Diámetro de la llanta
 *
 * Si se pega en el plugin "Code Snippets", quitar la primera línea: <?php
 */

function massio_normalizar_nombre_atributo( $nombre ) {
    return strtolower( remove_accents( trim( (string) $nombre ) ) );
}

function massio_nombre_filtros_neumaticos() {
    return array(
        'ancho'  => 'Ancho de sección',
        'alto'   => 'Relación de aspecto',
        'rodado' => 'Diámetro de la llanta',
    );
}

function massio_configuracion_yith_neumaticos() {
    return array(
        'ancho' => array(
            'nombre_local' => 'Ancho de sección',
            'taxonomia'    => 'pa_ancho',
        ),
        'alto' => array(
            'nombre_local' => 'Relación de aspecto',
            'taxonomia'    => 'pa_alto',
        ),
        'rodado' => array(
            'nombre_local' => 'Diámetro de la llanta',
            'taxonomia'    => 'pa_rodado',
        ),
    );
}

function massio_valor_yith_neumatico( $tipo, $valor ) {
    $valor = trim( (string) $valor );

    if ( in_array( $tipo, array( 'ancho', 'alto', 'rodado' ), true )
        && preg_match( '/-?\d+(?:[.,]\d+)?/u', $valor, $coincidencia ) ) {
        $valor = str_replace( ',', '.', $coincidencia[0] );

        if ( '.0' === substr( $valor, -2 ) ) {
            $valor = substr( $valor, 0, -2 );
        }
    }

    return $valor;
}

function massio_etiqueta_medida_neumatico( $tipo, $valor ) {
    $etiqueta = trim( (string) $valor );

    if ( 'ancho' === $tipo ) {
        $etiqueta = preg_replace( '/\s*mm\s*$/iu', '', $etiqueta );
    } elseif ( 'rodado' === $tipo ) {
        $etiqueta = preg_replace( '/\s*(?:"|pulgadas?)\s*$/iu', '', $etiqueta );
    }

    return trim( $etiqueta );
}

function massio_sincronizar_producto_con_yith( $producto_id ) {
    static $procesando = array();

    $producto_id = (int) $producto_id;

    if ( $producto_id <= 0 || isset( $procesando[ $producto_id ] ) ) {
        return;
    }

    $producto = wc_get_product( $producto_id );

    if ( ! $producto ) {
        return;
    }

    $procesando[ $producto_id ] = true;
    $atributos                  = $producto->get_attributes();
    $configuracion              = massio_configuracion_yith_neumaticos();
    $modificado                 = false;

    foreach ( $atributos as $clave => $atributo ) {
        if ( ! $atributo instanceof WC_Product_Attribute || $atributo->is_taxonomy() ) {
            continue;
        }

        $nombre_local = massio_normalizar_nombre_atributo( $atributo->get_name() );
        $tipo         = false;

        foreach ( $configuracion as $tipo_posible => $datos ) {
            if ( $nombre_local === massio_normalizar_nombre_atributo( $datos['nombre_local'] ) ) {
                $tipo = $tipo_posible;
                break;
            }
        }

        if ( false === $tipo ) {
            continue;
        }

        $taxonomia = $configuracion[ $tipo ]['taxonomia'];

        if ( ! taxonomy_exists( $taxonomia ) ) {
            continue;
        }

        $valores = array_values(
            array_unique(
                array_filter(
                    array_map(
                        static function ( $valor ) use ( $tipo ) {
                            return massio_valor_yith_neumatico( $tipo, $valor );
                        },
                        $atributo->get_options()
                    )
                )
            )
        );

        if ( empty( $valores ) ) {
            continue;
        }

        $terminos_ids = array();

        foreach ( $valores as $valor ) {
            $termino = term_exists( $valor, $taxonomia );

            if ( ! $termino ) {
                $termino = wp_insert_term( $valor, $taxonomia );
            }

            if ( is_wp_error( $termino ) ) {
                continue;
            }

            $terminos_ids[] = (int) ( is_array( $termino ) ? $termino['term_id'] : $termino );
        }

        if ( empty( $terminos_ids ) ) {
            continue;
        }

        wp_set_object_terms( $producto_id, $terminos_ids, $taxonomia, false );

        $atributo_global = new WC_Product_Attribute();
        $atributo_global->set_id( wc_attribute_taxonomy_id_by_name( $taxonomia ) );
        $atributo_global->set_name( $taxonomia );
        $atributo_global->set_options( $terminos_ids );
        $atributo_global->set_position( $atributo->get_position() );
        $atributo_global->set_visible( true );
        $atributo_global->set_variation( $atributo->get_variation() );

        unset( $atributos[ $clave ] );
        $atributos[ $taxonomia ] = $atributo_global;
        $modificado = true;
    }

    if ( $modificado ) {
        $producto->set_attributes( $atributos );
        $producto->save();
        massio_limpiar_cache_neumaticos();
    }

    unset( $procesando[ $producto_id ] );
}

add_action( 'woocommerce_new_product', 'massio_sincronizar_producto_con_yith', 20 );
add_action( 'woocommerce_update_product', 'massio_sincronizar_producto_con_yith', 20 );

add_action(
    'wp_loaded',
    function () {
        $version_migracion = 'massio_yith_neumaticos_ml_v1';

        if ( get_option( $version_migracion ) ) {
            return;
        }

        $productos_ids = wc_get_products(
            array(
                'status' => array( 'publish', 'draft', 'pending', 'private' ),
                'limit'  => -1,
                'return' => 'ids',
            )
        );

        foreach ( $productos_ids as $producto_id ) {
            massio_sincronizar_producto_con_yith( $producto_id );
        }

        update_option( $version_migracion, gmdate( 'c' ), false );
    },
    30
);

function massio_catalogo_neumaticos() {
    $cache_key = 'massio_catalogo_neumaticos_v3';
    $catalogo  = get_transient( $cache_key );

    if ( false !== $catalogo && is_array( $catalogo ) ) {
        return $catalogo;
    }

    $objetivos = massio_nombre_filtros_neumaticos();
    $buscados  = array();

    foreach ( $objetivos as $clave => $nombre ) {
        $buscados[ $clave ] = massio_normalizar_nombre_atributo( $nombre );
    }

    $catalogo = array(
        'opciones'  => array(
            'ancho'  => array(),
            'alto'   => array(),
            'rodado' => array(),
        ),
        'productos' => array(),
    );

    $productos = wc_get_products(
        array(
            'status' => 'publish',
            'limit'  => -1,
            'return' => 'objects',
        )
    );

    foreach ( $productos as $producto ) {
        $medidas = array(
            'ancho'  => array(),
            'alto'   => array(),
            'rodado' => array(),
        );

        foreach ( $producto->get_attributes() as $atributo ) {
            if ( ! $atributo instanceof WC_Product_Attribute ) {
                continue;
            }

            $nombre_atributo = massio_normalizar_nombre_atributo( $atributo->get_name() );
            $tipo            = false;

            if ( $atributo->is_taxonomy() ) {
                foreach ( massio_configuracion_yith_neumaticos() as $tipo_posible => $datos ) {
                    if ( $atributo->get_name() === $datos['taxonomia'] ) {
                        $tipo = $tipo_posible;
                        break;
                    }
                }
            } else {
                $tipo = array_search( $nombre_atributo, $buscados, true );
            }

            if ( false === $tipo ) {
                continue;
            }

            if ( $atributo->is_taxonomy() ) {
                $valores = wc_get_product_terms(
                    $producto->get_id(),
                    $atributo->get_name(),
                    array( 'fields' => 'names' )
                );
            } else {
                $valores = $atributo->get_options();
            }

            if ( is_wp_error( $valores ) ) {
                $valores = array();
            }

            foreach ( $valores as $valor ) {
                $valor = trim( (string) $valor );

                if ( '' === $valor ) {
                    continue;
                }

                $medidas[ $tipo ][] = $valor;
                $catalogo['opciones'][ $tipo ][ $valor ] =
                    massio_etiqueta_medida_neumatico( $tipo, $valor );
            }
        }

        if ( array_filter( $medidas ) ) {
            $catalogo['productos'][ $producto->get_id() ] = $medidas;
        }
    }

    foreach ( $catalogo['opciones'] as &$opciones ) {
        asort( $opciones, SORT_NATURAL | SORT_FLAG_CASE );
    }
    unset( $opciones );

    set_transient( $cache_key, $catalogo, 15 * MINUTE_IN_SECONDS );

    return $catalogo;
}

function massio_limpiar_cache_neumaticos() {
    delete_transient( 'massio_catalogo_neumaticos_v3' );
}

add_action( 'woocommerce_new_product', 'massio_limpiar_cache_neumaticos' );
add_action( 'woocommerce_update_product', 'massio_limpiar_cache_neumaticos' );
add_action( 'woocommerce_delete_product', 'massio_limpiar_cache_neumaticos' );

add_action(
    'woocommerce_product_query',
    function ( $query ) {
        $seleccion = array(
            'ancho'  => isset( $_GET['neumatico_ancho'] )
                ? sanitize_text_field( wp_unslash( $_GET['neumatico_ancho'] ) ) : '',
            'alto'   => isset( $_GET['neumatico_alto'] )
                ? sanitize_text_field( wp_unslash( $_GET['neumatico_alto'] ) ) : '',
            'rodado' => isset( $_GET['neumatico_rodado'] )
                ? sanitize_text_field( wp_unslash( $_GET['neumatico_rodado'] ) ) : '',
        );

        if ( ! array_filter( $seleccion ) ) {
            return;
        }

        $catalogo     = massio_catalogo_neumaticos();
        $coincidentes = array();

        foreach ( $catalogo['productos'] as $producto_id => $medidas ) {
            $coincide = true;

            foreach ( $seleccion as $tipo => $valor_elegido ) {
                if ( '' === $valor_elegido ) {
                    continue;
                }

                $valores_producto = array_map(
                    static function ( $valor ) {
                        return strtolower( trim( (string) $valor ) );
                    },
                    $medidas[ $tipo ] ?? array()
                );

                if ( ! in_array( strtolower( trim( $valor_elegido ) ), $valores_producto, true ) ) {
                    $coincide = false;
                    break;
                }
            }

            if ( $coincide ) {
                $coincidentes[] = (int) $producto_id;
            }
        }

        $post_in_actual = $query->get( 'post__in' );

        if ( is_array( $post_in_actual ) && ! empty( $post_in_actual ) ) {
            $coincidentes = array_values( array_intersect( $post_in_actual, $coincidentes ) );
        }

        $query->set( 'post__in', ! empty( $coincidentes ) ? $coincidentes : array( 0 ) );
    }
);

add_shortcode(
    'buscador_neumaticos',
    function () {
        $catalogo = massio_catalogo_neumaticos();
        $opciones = $catalogo['opciones'];

        $seleccion = array(
            'ancho'  => isset( $_GET['neumatico_ancho'] )
                ? sanitize_text_field( wp_unslash( $_GET['neumatico_ancho'] ) ) : '',
            'alto'   => isset( $_GET['neumatico_alto'] )
                ? sanitize_text_field( wp_unslash( $_GET['neumatico_alto'] ) ) : '',
            'rodado' => isset( $_GET['neumatico_rodado'] )
                ? sanitize_text_field( wp_unslash( $_GET['neumatico_rodado'] ) ) : '',
        );

        $tienda_url = 'https://tiendamassio.com.ar/tienda/';

        ob_start();
        ?>
        <div class="buscador-neumaticos">
            <div class="buscador-imagen">
                <img
                    src="https://www.gomeriamassio.com.ar/wp-content/uploads/2025/10/WhatsApp-Image-2025-10-09-at-11.59.25.jpeg"
                    alt="Guía de medidas de neumáticos"
                >
            </div>

            <div class="buscador-formulario">
                <h2>Buscá tu neumático</h2>

                <p
                    id="buscador-error"
                    style="display:none;color:red;font-weight:bold;margin-bottom:10px;"
                ></p>

                <form id="form-buscador-neumaticos" action="<?php echo esc_url( $tienda_url ); ?>" method="get">
                    <?php
                    $titulos = array(
                        'ancho'  => 'Seleccionar Ancho',
                        'alto'   => 'Seleccionar Alto',
                        'rodado' => 'Seleccionar Rodado',
                    );

                    foreach ( array( 'ancho', 'alto', 'rodado' ) as $tipo ) :
                        ?>
                        <select id="<?php echo esc_attr( $tipo ); ?>" name="neumatico_<?php echo esc_attr( $tipo ); ?>">
                            <option value=""><?php echo esc_html( $titulos[ $tipo ] ); ?></option>
                            <?php foreach ( $opciones[ $tipo ] as $valor_real => $etiqueta ) : ?>
                                <option
                                    value="<?php echo esc_attr( $valor_real ); ?>"
                                    <?php selected( $seleccion[ $tipo ], $valor_real ); ?>
                                >
                                    <?php echo esc_html( $etiqueta ); ?>
                                </option>
                            <?php endforeach; ?>
                        </select>
                    <?php endforeach; ?>

                    <button type="submit" id="buscar-neumatico">Buscar Neumático</button>
                </form>
            </div>
        </div>

        <script>
        (() => {
            const formulario = document.getElementById("form-buscador-neumaticos");
            if (!formulario) return;

            formulario.addEventListener("submit", (evento) => {
                evento.preventDefault();

                const ancho = document.getElementById("ancho").value;
                const alto = document.getElementById("alto").value;
                const rodado = document.getElementById("rodado").value;
                const error = document.getElementById("buscador-error");

                if (!ancho && !alto && !rodado) {
                    error.textContent = "Por favor seleccioná al menos un valor.";
                    error.style.display = "block";
                    return;
                }

                const url = new URL(<?php echo wp_json_encode( $tienda_url ); ?>);
                if (ancho) url.searchParams.set("neumatico_ancho", ancho);
                if (alto) url.searchParams.set("neumatico_alto", alto);
                if (rodado) url.searchParams.set("neumatico_rodado", rodado);

                fetch(url.toString())
                    .then((respuesta) => respuesta.text())
                    .then((html) => {
                        if (html.includes("woocommerce-no-products-found")) {
                            error.textContent = "No se encontraron productos para esa combinación.";
                            error.style.display = "block";
                            return;
                        }
                        window.location.href = url.toString();
                    })
                    .catch(() => {
                        window.location.href = url.toString();
                    });
            });
        })();
        </script>

        <style>
        .buscador-neumaticos {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 30px;
            background: #f5f5f5;
            padding: 20px;
            border-radius: 12px;
            margin-bottom: 20px;
            flex-wrap: wrap;
        }
        .buscador-imagen img {
            max-width: 300px;
            height: auto;
        }
        .buscador-formulario {
            display: flex;
            flex: 1;
            min-width: 250px;
            flex-direction: column;
            gap: 10px;
        }
        .buscador-formulario h2 {
            margin: 0 0 10px;
            font-size: 1.3rem;
        }
        .buscador-formulario select,
        .buscador-formulario button {
            width: 100%;
            margin: 12px 0;
            padding: 10px;
            border: 1px solid #ccc;
            border-radius: 8px;
            font-size: 1rem;
        }
        .buscador-formulario button {
            background: #000;
            color: #fff;
            cursor: pointer;
            font-weight: 700;
        }
        .buscador-formulario button:hover {
            background: #333;
        }
        @media (max-width: 768px) {
            .buscador-neumaticos {
                flex-direction: column;
                text-align: center;
            }
            .buscador-imagen img {
                margin-bottom: 10px;
            }
        }
        </style>
        <?php

        return ob_get_clean();
    }
);
