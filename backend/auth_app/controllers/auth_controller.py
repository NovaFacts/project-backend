from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from auth_app.services.auth_service import AuthService

@api_view(['POST'])
def login(request):
    correo = request.data.get('correo')
    password = request.data.get('password')

    # Validación de campos vacíos o nulos
    if not correo or not password:
        return Response(
            {"success": False, "message": "Faltan credenciales (correo y password requeridos)"},
            status=status.HTTP_400_BAD_REQUEST
        )

    # Llamada al servicio de autenticación
    result = AuthService.authenticate(correo, password)

    if result["success"]:
        return Response(result, status=status.HTTP_200_OK)
    else:
        return Response(result, status=status.HTTP_401_UNAUTHORIZED)
