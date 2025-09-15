from PyQt5.QtWidgets import QWidget, QPushButton, QVBoxLayout, QDesktopWidget
from PyQt5.QtCore import pyqtSignal


class LoginWindow(QWidget):
    login_success = pyqtSignal()

    def __init__(self):
        super().__init__()
        self.initUI()

    def initUI(self):
        self.setWindowTitle('S2B 상품 정보 AI 생성기 - 로그인')

        self.login_button = QPushButton('카카오 로그인')
        self.login_button.setMinimumHeight(40)

        layout = QVBoxLayout()
        layout.addWidget(self.login_button)
        self.setLayout(layout)

        self.login_button.clicked.connect(self.handle_login)

        self.resize(300, 100)
        self.center()

    def handle_login(self):
        self.login_success.emit()

    def center(self):
        qr = self.frameGeometry()
        cp = QDesktopWidget().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())