# Agente de citas-api

- Java 21, Spring Boot 3.5, Maven, JPA, Flyway y MySQL 8.4.
- Mantener separación entre dominio/aplicación y adaptadores REST/persistencia.
- Cambios de esquema requieren migración Flyway compatible con MySQL y pruebas H2.
- Secretos únicamente por variables de entorno; nunca registrar tokens o contraseñas.
- Controladores traducen HTTP; las reglas deben validarse también en aplicación/persistencia.
- Antes de implementar, localizar la HU aprobada y su DoD. Si no existe aprobación, dejar el estado documental como pendiente.
- Verificar con `mvn test` dentro del entorno Maven del proyecto.
