import { render, screen } from '@testing-library/react'
import { App } from './App'

describe('App', () => {
  it('asks to open the app in Telegram when there is no initData', async () => {
    window.Telegram = undefined

    render(<App />)

    expect(await screen.findByText('Ilovani Telegram orqali oching')).toBeInTheDocument()
  })
})
