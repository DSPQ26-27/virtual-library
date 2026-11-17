const ROLES_PERSONAL = ["LIBRARIAN", "ADMIN"];

/** true si el usuario es bibliotecario o administrador (los roles que acepta el backend). */
export function canManageCatalog(user) {
  return Array.isArray(user?.roles) && user.roles.some((role) => ROLES_PERSONAL.includes(role));
}

/** true si el usuario es estudiante: solo ellos pueden pedir préstamos (US07). */
export function isStudent(user) {
  return Array.isArray(user?.roles) && user.roles.includes("STUDENT");
}
