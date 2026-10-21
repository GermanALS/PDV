package com.pdv.pos.auth

// Contrasena de bootstrap publica, mismo valor que
// backend/alembic/versions/0009_seed_admin_usuario.py: un dispositivo en
// modo LOCAL que nunca sincroniza con el backend necesita el mismo punto de
// entrada, o nunca podria llegar a la pantalla de Usuarios para crear el
// primer administrador real. Por ser publica, el admin nace con
// debeCambiarPassword y no puede volver a elegirla (PLAN.md Parte 37, A-3).
const val ADMIN_BOOTSTRAP_USERNAME = "admin"
const val ADMIN_BOOTSTRAP_PASSWORD = "admin123"
