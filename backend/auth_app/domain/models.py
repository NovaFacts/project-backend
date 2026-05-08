from django.db import models

class Ingesoft1User(models.Model):
    correo = models.EmailField(unique=True)
    password_hash = models.CharField(max_length=128)
    secret_phrase = models.CharField(max_length=255)

    class Meta:
        db_table = 'ingesoft1_users'
