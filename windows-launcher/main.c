#include <windows.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE h, HINSTANCE p, PWSTR cmd, int show) {
    wchar_t dir[MAX_PATH], javaw[MAX_PATH], jar[MAX_PATH], line[3 * MAX_PATH];
    GetModuleFileNameW(NULL, dir, MAX_PATH);
    wchar_t *s = wcsrchr(dir, L'\\'); if (s) *s = 0;
    swprintf(javaw, MAX_PATH, L"%ls\\runtime\\bin\\javaw.exe", dir);
    swprintf(jar, MAX_PATH, L"%ls\\app\\Reloj.jar", dir);
    if (GetFileAttributesW(javaw) == INVALID_FILE_ATTRIBUTES || GetFileAttributesW(jar) == INVALID_FILE_ATTRIBUTES) {
        MessageBoxW(NULL, L"No se encontraron las carpetas 'runtime' y 'app'.\nMantén Reloj.exe dentro de su carpeta original.", L"Reloj", MB_ICONERROR);
        return 1;
    }
    swprintf(line, 3 * MAX_PATH, L"\"%ls\" \"-Dreloj.fontdir=%ls\\app\\fonts\" -jar \"%ls\"", javaw, dir, jar);
    STARTUPINFOW si = { sizeof(si) }; PROCESS_INFORMATION pi;
    if (!CreateProcessW(NULL, line, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL, L"No se pudo iniciar Reloj.", L"Reloj", MB_ICONERROR);
        return 1;
    }
    CloseHandle(pi.hThread); CloseHandle(pi.hProcess);
    return 0;
}
