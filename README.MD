# JCE Citizen Backend - Easypanel Deployment Guide

Este microservicio provee la API de consulta de ciudadanos sincronizada con la JCE.

## 🚀 Despliegue en Easypanel

Sigue estos pasos para desplegar en Hostinger/Easypanel:

1. **Crear Proyecto:** En Easypanel, crea un nuevo proyecto llamado `JCE-Microservice`.
2. **Nueva App:** Selecciona "Docker Compose" (o importa este repositorio si prefieres construir desde el Dockerfile).
3. **Copiar Docker Compose:** Utiliza el archivo `docker-compose.yml` incluido en este repositorio.
4. **Environment Variables:** Copia el contenido de `.env.example` a la sección **Environment** de tu App en Easypanel y ajusta los valores (especialmente `JWT_SECRET` y las contraseñas de DB).
5. **Configuración de Red:** 
   - El servicio `jce-app` corre internamente en el puerto **8080**.
   - En Easypanel, asigna un dominio a `jce-app` (ej: `jce-api.tusistema.com`).
6. **Health Check:** 
   - Endpoint: `/actuator/health`
   - Path: `/`

## ⚙️ Configuración en AbcPortal

Una vez desplegado, actualiza tu `.env` de AbcPortal:

```env
JCE_API_URL=https://jce-api.tusistema.com/api/v1
JCE_API_USER=anthonyatras@gmail.com
JCE_API_PASS=Emulador1
```

---
*Mantenido por [jwdejesus11](https://github.com/jwdejesus11)*
