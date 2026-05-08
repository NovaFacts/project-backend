from django.urls import path
from auth_app.controllers.auth_controller import login

urlpatterns = [
    # Se recomienda usar 'login/' con barra inclinada al final
    path('login/', login, name='login'),
]
