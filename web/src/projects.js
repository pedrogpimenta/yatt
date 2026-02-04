export function formatProjectLabel(project) {
  if (!project) return ''
  const parts = [project.name]
  if (project.type) {
    parts.push(project.type)
  }
  if (project.client_name) {
    parts.push(project.client_name)
  }
  return parts.join(' - ')
}
