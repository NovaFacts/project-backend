import hashlib
from auth_app.repositories.user_repository import UserRepository

class AuthService:
    
    @staticmethod
    def hash_password(password):
        # Es necesario codificar el string a bytes antes de aplicar el hash
        return hashlib.sha512(password.encode('utf-8')).hexdigest()

    @staticmethod
    def authenticate(correo, password):
        user = UserRepository.find_by_correo(correo)
        
        if not user:
            return {"success": False, "message": "Usuario no encontrado"}
            
        hashed_password = AuthService.hash_password(password)
        
        if user.password_hash != hashed_password:
            return {"success": False, "message": "Contraseña incorrecta"}
            
        return {
            "success": True, 
            "secret_phrase": user.secret_phrase
        }
