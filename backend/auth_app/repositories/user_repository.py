from auth_app.domain.models import Ingesoft1User

class UserRepository:
    @staticmethod
    def find_by_correo(correo):
        return Ingesoft1User.objects.filter(correo=correo).first()

    @staticmethod
    def create_user(correo, password_hash, secret_phrase):
        return Ingesoft1User.objects.create(
            correo=correo,
            password_hash=password_hash,
            secret_phrase=secret_phrase
        )
