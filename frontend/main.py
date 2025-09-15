import sys
from PyQt5.QtWidgets import QApplication

# 각 창 클래스들을 import
from login_window import LoginWindow
from main_window import MainWindow


class MainController:
    def __init__(self):
        self.login_win = LoginWindow()
        self.main_win = None

        self.login_win.login_success.connect(self.show_main_window)

    def show_login_window(self):
        self.login_win.show()

    def show_main_window(self):
        if not self.main_win:
            self.main_win = MainWindow()

        self.login_win.close()
        self.main_win.show()


if __name__ == '__main__':
    app = QApplication(sys.argv)
    controller = MainController()
    controller.show_login_window()
    sys.exit(app.exec_())